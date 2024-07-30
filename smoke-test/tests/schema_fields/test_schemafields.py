import logging
import os
import tempfile
import time
from random import randint

import datahub.metadata.schema_classes as models
import pytest
from datahub.emitter.mce_builder import make_dataset_urn, make_schema_field_urn
from datahub.emitter.mcp import MetadataChangeProposalWrapper
from datahub.ingestion.api.common import PipelineContext, RecordEnvelope
from datahub.ingestion.api.sink import NoopWriteCallback
from datahub.ingestion.graph.client import DatahubClientConfig, DataHubGraph
from datahub.ingestion.sink.file import FileSink, FileSinkConfig

from tests.utils import (
    delete_urns_from_file,
    get_gms_url,
    get_sleep_info,
    ingest_file_via_rest,
    wait_for_writes_to_sync,
)

logger = logging.getLogger(__name__)


start_index = randint(10, 10000)
dataset_urns = [
    make_dataset_urn("snowflake", f"table_foo_{i}")
    for i in range(start_index, start_index + 10)
]


class FileEmitter:
    def __init__(self, filename: str) -> None:
        self.sink: FileSink = FileSink(
            ctx=PipelineContext(run_id="create_test_data"),
            config=FileSinkConfig(filename=filename),
        )

    def emit(self, event):
        self.sink.write_record_async(
            record_envelope=RecordEnvelope(record=event, metadata={}),
            write_callback=NoopWriteCallback(),
        )

    def close(self):
        self.sink.close()


@pytest.fixture(scope="module")
def chart_urn():
    return "urn:li:chart:(looker,chart_foo)"


@pytest.fixture(scope="module")
def upstream_schema_field_urn():
    return make_schema_field_urn(make_dataset_urn("snowflake", "table_bar"), "field1")


def create_test_data(filename: str, chart_urn: str, upstream_schema_field_urn: str):
    documentation_mcp = MetadataChangeProposalWrapper(
        entityUrn=upstream_schema_field_urn,
        aspect=models.DocumentationClass(
            documentations=[
                models.DocumentationAssociationClass(
                    documentation="test documentation",
                    attribution=models.MetadataAttributionClass(
                        time=int(time.time() * 1000),
                        actor="urn:li:corpuser:datahub",
                        source="urn:li:dataHubAction:documentation_propagation",
                    ),
                )
            ]
        ),
    )

    input_fields_mcp = MetadataChangeProposalWrapper(
        entityUrn=chart_urn,
        aspect=models.InputFieldsClass(
            fields=[
                models.InputFieldClass(
                    schemaFieldUrn=upstream_schema_field_urn,
                    schemaField=models.SchemaFieldClass(
                        fieldPath="field1",
                        type=models.SchemaFieldDataTypeClass(models.StringTypeClass()),
                        nativeDataType="STRING",
                    ),
                )
            ]
        ),
    )

    file_emitter = FileEmitter(filename)
    for mcps in [documentation_mcp, input_fields_mcp]:
        file_emitter.emit(mcps)

    file_emitter.close()


sleep_sec, sleep_times = get_sleep_info()


@pytest.fixture(scope="module", autouse=False)
def ingest_cleanup_data(request, chart_urn, upstream_schema_field_urn):
    new_file, filename = tempfile.mkstemp(suffix=".json")
    try:
        create_test_data(filename, chart_urn, upstream_schema_field_urn)
        print("ingesting schema fields test data")
        ingest_file_via_rest(filename)
        yield
        print("removing schema fields test data")
        delete_urns_from_file(filename)
        wait_for_writes_to_sync()
    finally:
        os.remove(filename)


@pytest.mark.dependency()
def test_healthchecks(wait_for_healthchecks):
    # Call to wait_for_healthchecks fixture will do the actual functionality.
    pass


def get_gql_query(filename: str) -> str:
    with open(filename) as fp:
        return fp.read()


def validate_schema_field_urn_for_chart(
    graph: DataHubGraph, chart_urn: str, upstream_schema_field_urn: str
) -> None:
    # Validate listing
    result = graph.execute_graphql(
        get_gql_query("tests/schema_fields/queries/get_chart_field.gql"),
        {"urn": chart_urn},
    )
    assert "chart" in result
    assert "inputFields" in result["chart"]
    assert len(result["chart"]["inputFields"]["fields"]) == 1
    assert (
        result["chart"]["inputFields"]["fields"][0]["schemaField"]["schemaFieldEntity"][
            "urn"
        ]
        == upstream_schema_field_urn
    )
    assert (
        result["chart"]["inputFields"]["fields"][0]["schemaField"]["schemaFieldEntity"][
            "fieldPath"
        ]
        == "field1"
    )
    assert (
        result["chart"]["inputFields"]["fields"][0]["schemaFieldUrn"]
        == upstream_schema_field_urn
    )
    assert (
        result["chart"]["inputFields"]["fields"][0]["schemaField"]["schemaFieldEntity"][
            "documentation"
        ]["documentations"][0]["documentation"]
        == "test documentation"
    )


# @tenacity.retry(
#     stop=tenacity.stop_after_attempt(sleep_times), wait=tenacity.wait_fixed(sleep_sec)
# )
@pytest.mark.dependency(depends=["test_healthchecks"])
def test_schema_field_gql_mapper_for_charts(
    ingest_cleanup_data, chart_urn, upstream_schema_field_urn
):
    graph: DataHubGraph = DataHubGraph(config=DatahubClientConfig(server=get_gms_url()))

    validate_schema_field_urn_for_chart(graph, chart_urn, upstream_schema_field_urn)
