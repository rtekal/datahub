import logging
import re
from typing import Any, ClassVar, Dict, Set

from liquid import Undefined
from liquid.exceptions import LiquidSyntaxError

from datahub.ingestion.source.looker.looker_liquid_tag import (
    CustomTagException,
    create_template,
)
from datahub.ingestion.source.looker.lookml_config import DERIVED_VIEW_PATTERN

logger = logging.getLogger(__name__)


class SpecialVariable:
    SPECIAL_VARIABLE_PATTERN: ClassVar[
        str
    ] = r"\b\w+(\.\w+)*\._(is_selected|in_query|is_filtered)\b"
    liquid_variable: dict

    def __init__(self, liquid_variable):
        self.liquid_variable = liquid_variable

    def _create_new_liquid_variables_with_default(
        self,
        variables: Set[str],
    ) -> dict:
        new_dict = {**self.liquid_variable}

        for variable in variables:
            keys = variable.split(
                "."
            )  # variable is defined as view._is_selected or view.field_name._is_selected

            current_dict: dict = new_dict

            for key in keys[:-1]:

                if key not in current_dict:
                    current_dict[key] = {}

                current_dict = current_dict[key]

            if keys[-1] not in current_dict:
                current_dict[keys[-1]] = True

        logger.debug("added special variables in liquid_variable dictionary")

        return new_dict

    def liquid_variable_with_default(self, text: str) -> dict:
        variables: Set[str] = set(
            [
                text[m.start() : m.end()]
                for m in re.finditer(SpecialVariable.SPECIAL_VARIABLE_PATTERN, text)
            ]
        )

        # if set is empty then no special variables are found.
        if not variables:
            return self.liquid_variable

        return self._create_new_liquid_variables_with_default(variables=variables)


def resolve_liquid_variable(text: str, liquid_variable: Dict[Any, Any]) -> str:
    # Set variable value to NULL if not present in liquid_variable dictionary
    Undefined.__str__ = lambda instance: "NULL"  # type: ignore
    try:
        # See is there any special boolean variables are there in the text like _in_query, _is_selected, and
        # _is_filtered. Refer doc for more information
        # https://cloud.google.com/looker/docs/liquid-variable-reference#usage_of_in_query_is_selected_and_is_filtered
        # update in liquid_variable with there default values
        liquid_variable = SpecialVariable(liquid_variable).liquid_variable_with_default(
            text
        )
        # Resolve liquid template
        return create_template(text).render(liquid_variable)
    except LiquidSyntaxError as e:
        logger.warning(f"Unsupported liquid template encountered. error [{e.message}]")
        # TODO: There are some tag specific to looker and python-liquid library does not understand them. currently
        #  we are not parsing such liquid template.
        #
        # See doc: https://cloud.google.com/looker/docs/templated-filters and look for { % condition region %}
        # order.region { % endcondition %}
    except CustomTagException as e:
        logger.warning(e)
        logger.debug(e, exc_info=e)

    return text


def _drop_derived_view_pattern(value: str) -> str:
    # Drop ${ and }
    return re.sub(DERIVED_VIEW_PATTERN, r"\1", value)


def _complete_incomplete_sql(raw_view: dict, sql: str) -> str:

    # Looker supports sql fragments that omit the SELECT and FROM parts of the query
    # Add those in if we detect that it is missing
    sql_query: str = sql

    if not re.search(r"SELECT\s", sql_query, flags=re.I):
        # add a SELECT clause at the beginning
        sql_query = f"SELECT {sql}"

    if not re.search(r"FROM\s", sql_query, flags=re.I):
        # add a FROM clause at the end
        sql_query = f"{sql_query} FROM {raw_view['name']}"

    return _drop_derived_view_pattern(sql_query)


def resolve_liquid_variable_in_view_dict(
    raw_view: dict, liquid_variable: Dict[Any, Any]
) -> None:
    if "views" not in raw_view:
        return

    for view in raw_view["views"]:
        if "sql_table_name" in view:
            view["datahub_transformed_sql_table_name"] = resolve_liquid_variable(
                text=view["sql_table_name"],
                liquid_variable=liquid_variable,
            )  # keeping original sql_table_name as is to avoid any visualization issue later

            view["datahub_transformed_sql_table_name"] = _drop_derived_view_pattern(
                value=view["datahub_transformed_sql_table_name"]
            )

        if "derived_table" in view and "sql" in view["derived_table"]:
            # In sql we don't need to remove the extra spaces as sql parser takes care of extra spaces and \n
            # while generating URN from sql
            view["derived_table"]["datahub_transformed_sql"] = resolve_liquid_variable(
                text=view["derived_table"]["sql"], liquid_variable=liquid_variable
            )  # keeping original sql as is, so that on UI sql will be shown same is it is visible on looker portal

            view["derived_table"]["datahub_transformed_sql"] = _complete_incomplete_sql(
                raw_view=view, sql=view["derived_table"]["datahub_transformed_sql"]
            )
