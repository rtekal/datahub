import pathlib
from datetime import datetime, timezone

import pytest
from freezegun import freeze_time

import datahub.emitter.mce_builder as builder
from datahub.metadata.urns import CorpUserUrn, DatasetUrn
from datahub.sql_parsing.sql_parsing_aggregator_v2 import (
    QueryLogSetting,
    SqlParsingAggregator,
)
from tests.test_helpers import mce_helpers

RESOURCE_DIR = pathlib.Path(__file__).parent / "aggregator_goldens"
FROZEN_TIME = "2024-02-06 01:23:45"


def _ts(ts: int) -> datetime:
    return datetime.fromtimestamp(ts, tz=timezone.utc)


@freeze_time(FROZEN_TIME)
def test_basic_lineage(pytestconfig: pytest.Config) -> None:
    aggregator = SqlParsingAggregator(
        platform="redshift",
        platform_instance=None,
        env=builder.DEFAULT_ENV,
        generate_lineage=True,
        generate_usage_statistics=False,
        generate_operations=False,
    )

    aggregator.add_observed_query(
        query="create table foo as select a, b from bar",
        default_db="dev",
        default_schema="public",
    )

    mcps = list(aggregator.gen_metadata())

    mce_helpers.check_goldens_stream(
        pytestconfig,
        outputs=mcps,
        golden_path=RESOURCE_DIR / "test_basic_lineage.json",
    )


@freeze_time(FROZEN_TIME)
def test_overlapping_inserts(pytestconfig: pytest.Config) -> None:
    aggregator = SqlParsingAggregator(
        platform="redshift",
        platform_instance=None,
        env=builder.DEFAULT_ENV,
        generate_lineage=True,
        generate_usage_statistics=False,
        generate_operations=False,
    )

    aggregator.add_observed_query(
        query="insert into downstream (a, b) select a, b from upstream1",
        default_db="dev",
        default_schema="public",
        query_timestamp=_ts(20),
    )
    aggregator.add_observed_query(
        query="insert into downstream (a, c) select a, c from upstream2",
        default_db="dev",
        default_schema="public",
        query_timestamp=_ts(25),
    )

    mcps = list(aggregator.gen_metadata())

    mce_helpers.check_goldens_stream(
        pytestconfig,
        outputs=mcps,
        golden_path=RESOURCE_DIR / "test_overlapping_inserts.json",
    )


@freeze_time(FROZEN_TIME)
def test_temp_table(pytestconfig: pytest.Config) -> None:
    aggregator = SqlParsingAggregator(
        platform="redshift",
        platform_instance=None,
        env=builder.DEFAULT_ENV,
        generate_lineage=True,
        generate_usage_statistics=False,
        generate_operations=False,
    )

    aggregator._schema_resolver.add_raw_schema_info(
        DatasetUrn("redshift", "dev.public.bar").urn(),
        {"a": "int", "b": "int", "c": "int"},
    )

    aggregator.add_observed_query(
        query="create table foo as select a, 2*b as b from bar",
        default_db="dev",
        default_schema="public",
        session_id="session1",
    )
    aggregator.add_observed_query(
        query="create temp table foo as select a, b+c as c from bar",
        default_db="dev",
        default_schema="public",
        session_id="session2",
    )
    aggregator.add_observed_query(
        query="create table foo_session2 as select * from foo",
        default_db="dev",
        default_schema="public",
        session_id="session2",
    )
    aggregator.add_observed_query(
        query="create table foo_session3 as select * from foo",
        default_db="dev",
        default_schema="public",
        session_id="session3",
    )

    # foo_session2 should come from bar (via temp table foo), have columns a and c, and depend on bar.{a,b,c}
    # foo_session3 should come from foo, have columns a and b, and depend on bar.b

    mcps = list(aggregator.gen_metadata())

    mce_helpers.check_goldens_stream(
        pytestconfig,
        outputs=mcps,
        golden_path=RESOURCE_DIR / "test_temp_table.json",
    )


@freeze_time(FROZEN_TIME)
def test_aggregate_operations(pytestconfig: pytest.Config) -> None:
    aggregator = SqlParsingAggregator(
        platform="redshift",
        platform_instance=None,
        env=builder.DEFAULT_ENV,
        generate_lineage=False,
        generate_queries=False,
        generate_usage_statistics=False,
        generate_operations=True,
    )

    aggregator.add_observed_query(
        query="create table foo as select a, b from bar",
        default_db="dev",
        default_schema="public",
        query_timestamp=_ts(20),
        user=CorpUserUrn("user1"),
    )
    aggregator.add_observed_query(
        query="create table foo as select a, b from bar",
        default_db="dev",
        default_schema="public",
        query_timestamp=_ts(25),
        user=CorpUserUrn("user2"),
    )
    aggregator.add_observed_query(
        query="create table foo as select a, b+1 as b from bar",
        default_db="dev",
        default_schema="public",
        query_timestamp=_ts(26),
        user=CorpUserUrn("user3"),
    )

    # The first query will basically be ignored, as it's a duplicate of the second one.

    mcps = list(aggregator.gen_metadata())

    mce_helpers.check_goldens_stream(
        pytestconfig,
        outputs=mcps,
        golden_path=RESOURCE_DIR / "test_aggregate_operations.json",
    )


@freeze_time(FROZEN_TIME)
def test_view_lineage(pytestconfig: pytest.Config) -> None:
    aggregator = SqlParsingAggregator(
        platform="redshift",
        platform_instance=None,
        env=builder.DEFAULT_ENV,
        generate_lineage=True,
        generate_usage_statistics=False,
        generate_operations=False,
        query_log=QueryLogSetting.STORE_ALL,
    )

    aggregator.add_view_definition(
        view_urn=DatasetUrn("redshift", "dev.public.foo"),
        view_definition="create view foo as select a, b from bar",
        default_db="dev",
        default_schema="public",
    )

    aggregator._schema_resolver.add_raw_schema_info(
        urn=DatasetUrn("redshift", "dev.public.foo").urn(),
        schema_info={"a": "int", "b": "int"},
    )
    aggregator._schema_resolver.add_raw_schema_info(
        urn=DatasetUrn("redshift", "dev.public.bar").urn(),
        schema_info={"a": "int", "b": "int"},
    )

    # Because we have schema information, despite it being registered after the view definition,
    # the confidence score should be high.

    mcps = list(aggregator.gen_metadata())

    mce_helpers.check_goldens_stream(
        pytestconfig,
        outputs=mcps,
        golden_path=RESOURCE_DIR / "test_view_lineage.json",
    )
