from typing import List

from datahub.emitter.mcp import MetadataChangeProposalWrapper
from datahub.ingestion.api.workunit import MetadataWorkUnit
from datahub.ingestion.source.data_lake_common.data_lake_utils import ContainerWUCreator
from datahub.ingestion.source.data_lake_common.path_spec import PathSpec
from datahub.ingestion.source.s3.source import partitioned_folder_comparator


def test_partition_comparator_numeric_folder_name():
    folder1 = "3"
    folder2 = "12"
    assert partitioned_folder_comparator(folder1, folder2) == -1


def test_partition_multi_level_key():
    folder1 = "backup/metadata_aspect_v2/year=2023/month=01"
    folder2 = "backup/metadata_aspect_v2/year=2023/month=2"
    assert partitioned_folder_comparator(folder1, folder2) == -1


def test_partition_comparator_numeric_folder_name2():
    folder1 = "12"
    folder2 = "3"
    assert partitioned_folder_comparator(folder1, folder2) == 1


def test_partition_comparator_string_folder():
    folder1 = "myfolderB"
    folder2 = "myFolderA"
    assert partitioned_folder_comparator(folder1, folder2) == 1


def test_partition_comparator_string_same_folder():
    folder1 = "myFolderA"
    folder2 = "myFolderA"
    assert partitioned_folder_comparator(folder1, folder2) == 0


def test_partition_comparator_with_numeric_partition():
    folder1 = "year=3"
    folder2 = "year=12"
    assert partitioned_folder_comparator(folder1, folder2) == -1


def test_partition_comparator_with_padded_numeric_partition():
    folder1 = "year=03"
    folder2 = "year=12"
    assert partitioned_folder_comparator(folder1, folder2) == -1


def test_partition_comparator_with_equal_sign_in_name():
    folder1 = "month=12"
    folder2 = "year=0"
    assert partitioned_folder_comparator(folder1, folder2) == -1


def test_partition_comparator_with_string_partition():
    folder1 = "year=year2020"
    folder2 = "year=year2021"
    assert partitioned_folder_comparator(folder1, folder2) == -1


def test_path_spec():
    path_spec = PathSpec(
        include="s3://my-bucket/my-folder/year=*/month=*/day=*/*.csv",
        default_extension="csv",
    )
    path = "s3://my-bucket/my-folder/year=2022/month=10/day=11/my_csv.csv"
    assert path_spec.allowed(path)


def test_path_spec_dir_allowed():
    path_spec = PathSpec(
        include="s3://my-bucket/my-folder/year=*/month=*/day=*/*.csv",
        exclude=[
            "s3://my-bucket/my-folder/year=2022/month=12/day=11",
            "s3://my-bucket/my-folder/year=2022/month=10/**",
        ],
        default_extension="csv",
    )
    path = "s3://my-bucket/my-folder/year=2022/"
    assert path_spec.dir_allowed(path) is True, f"{path} should be allowed"

    path = "s3://my-bucket/my-folder/year=2022/month=12/"
    assert path_spec.dir_allowed(path) is True, f"{path} should be allowed"

    path = "s3://my-bucket/my-folder/year=2022/month=12/day=11/my_csv.csv"
    assert path_spec.dir_allowed(path) is False, f"{path} should be denied"

    path = "s3://my-bucket/my-folder/year=2022/month=12/day=10/"
    assert path_spec.dir_allowed(path) is True, f"{path} should be allowed"

    path = "s3://my-bucket/my-folder/year=2022/month=12/day=10/_temporary/"
    assert path_spec.dir_allowed(path) is False, f"{path} should be denied"

    path = "s3://my-bucket/my-folder/year=2022/month=10/day=10/"
    assert path_spec.dir_allowed(path) is False, f"{path} should be denied"


def test_container_generation_without_folders():
    cwu = ContainerWUCreator("s3", None, "PROD")
    mcps = cwu.create_container_hierarchy(
        "s3://my-bucket/my-file.json.gz", "urn:li:dataset:123"
    )

    def container_properties_filter(x: MetadataWorkUnit) -> bool:
        assert isinstance(x.metadata, MetadataChangeProposalWrapper)
        return x.metadata.aspectName == "containerProperties"

    container_properties: List = list(filter(container_properties_filter, mcps))
    assert len(container_properties) == 1
    assert container_properties[0].metadata.aspect.customProperties == {
        "bucket_name": "my-bucket",
        "env": "PROD",
        "platform": "s3",
    }


def test_container_generation_with_folder():
    cwu = ContainerWUCreator("s3", None, "PROD")
    mcps = cwu.create_container_hierarchy(
        "s3://my-bucket/my-dir/my-file.json.gz", "urn:li:dataset:123"
    )

    def container_properties_filter(x: MetadataWorkUnit) -> bool:
        assert isinstance(x.metadata, MetadataChangeProposalWrapper)
        return x.metadata.aspectName == "containerProperties"

    container_properties: List = list(filter(container_properties_filter, mcps))
    assert len(container_properties) == 2
    assert container_properties[0].metadata.aspect.customProperties == {
        "bucket_name": "my-bucket",
        "env": "PROD",
        "platform": "s3",
    }
    assert container_properties[1].metadata.aspect.customProperties == {
        "env": "PROD",
        "folder_abs_path": "my-bucket/my-dir",
        "platform": "s3",
    }


def test_container_generation_with_multiple_folders():
    cwu = ContainerWUCreator("s3", None, "PROD")
    mcps = cwu.create_container_hierarchy(
        "s3://my-bucket/my-dir/my-dir2/my-file.json.gz", "urn:li:dataset:123"
    )

    def container_properties_filter(x: MetadataWorkUnit) -> bool:
        assert isinstance(x.metadata, MetadataChangeProposalWrapper)
        return x.metadata.aspectName == "containerProperties"

    container_properties: List = list(filter(container_properties_filter, mcps))

    assert len(container_properties) == 3
    assert container_properties[0].metadata.aspect.customProperties == {
        "bucket_name": "my-bucket",
        "env": "PROD",
        "platform": "s3",
    }
    assert container_properties[1].metadata.aspect.customProperties == {
        "env": "PROD",
        "folder_abs_path": "my-bucket/my-dir",
        "platform": "s3",
    }
    assert container_properties[2].metadata.aspect.customProperties == {
        "env": "PROD",
        "folder_abs_path": "my-bucket/my-dir/my-dir2",
        "platform": "s3",
    }
