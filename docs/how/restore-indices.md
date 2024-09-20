# Restoring Search and Graph Indices from Local Database

If search or graph services go down or you have made changes to them that require reindexing, you can restore them from
the aspects stored in the local database.

When a new version of the aspect gets ingested, GMS initiates an MAE event for the aspect which is consumed to update
the search and graph indices. As such, we can fetch the latest version of each aspect in the local database and produce
MAE events corresponding to the aspects to restore the search and graph indices.

By default, restoring the indices from the local database will not remove any existing documents in
the search and graph indices that no longer exist in the local database, potentially leading to inconsistencies
between the search and graph indices and the local database.

## Quickstart

If you're using the quickstart images, you can use the `datahub` cli to restore the indices.

```shell
datahub docker quickstart --restore-indices
```

:::info
Using the `datahub` CLI to restore the indices when using the quickstart images will also clear the search and graph indices before restoring.
:::

See [this section](../quickstart.md#restore-datahub) for more information. 

## Docker-compose

If you are on a custom docker-compose deployment, run the following command (you need to checkout [the source repository](https://github.com/datahub-project/datahub)) from the root of the repo to send MAE for each aspect in the local database.

```shell
./docker/datahub-upgrade/datahub-upgrade.sh -u RestoreIndices
```

:::info
By default this command will not clear the search and graph indices before restoring, thous potentially leading to inconsistencies between the local database and the indices, in case aspects were previously deleted in the local database but were not removed from the correponding index.
:::

If you need to clear the search and graph indices before restoring, add `-a clean` to the end of the command. Please take note that the search and graph services might not be fully functional during reindexing when the indices are cleared.

```shell
./docker/datahub-upgrade/datahub-upgrade.sh -u RestoreIndices -a clean
```

Refer to this [doc](../../docker/datahub-upgrade/README.md#environment-variables) on how to set environment variables
for your environment.

## Kubernetes

Run `kubectl get cronjobs` to see if the restoration job template has been deployed. If you see results like below, you
are good to go.

```
NAME                                          SCHEDULE    SUSPEND   ACTIVE   LAST SCHEDULE   AGE
datahub-datahub-cleanup-job-template          * * * * *   True      0        <none>          2d3h
datahub-datahub-restore-indices-job-template  * * * * *   True      0        <none>          2d3h
```

If not, deploy latest helm charts to use this functionality.

Once restore indices job template has been deployed, run the following command to start a job that restores indices.

```shell
kubectl create job --from=cronjob/datahub-datahub-restore-indices-job-template datahub-restore-indices-adhoc
```

Once the job completes, your indices will have been restored.

:::info
By default the restore indices job template will not clear the search and graph indices before restoring, thous potentially leading to inconsistencies between the local database and the indices, in case aspects were previously deleted in the local database but were not removed from the correponding index.
:::

If you need to clear the search and graph indices before restoring, modify the `values.yaml` for your deployment and overwrite the default arguments of the restore indices job template to include the `-a clean` argument. Please take note that the search and graph services might not be fully functional during reindexing when the indices are cleared.

```yaml
datahubUpgrade:
  restoreIndices:
    image:
      args:
        - "-u"
        - "RestoreIndices"
        - "-a"
        - "batchSize=1000" # default value of datahubUpgrade.batchSize
        - "-a"
        - "batchDelayMs=100" # default value of datahubUpgrade.batchDelayMs
        - "-a"
        - "clean"
```

## Through API

See [Restore Indices API](../api/restli/restore-indices.md).