(google-page)=

# Google Cloud

## Credentials

Credentials for submitting requests to the Google Cloud Batch API are picked up from your environment using [Application Default Credentials](https://github.com/googleapis/google-auth-library-java#google-auth-library-oauth2-http). Application Default Credentials are designed to use the credentials most natural to the environment in which a tool runs.

The most common case will be to pick up your end-user Google credentials from your workstation. You can create these by running the command:

```bash
gcloud auth application-default login
```

and running through the authentication flow. This will write a credential file to your gcloud configuration directory that will be used for any tool you run on your workstation that picks up default credentials.

The next most common case would be when running on a Compute Engine VM. In this case, Application Default Credentials will pick up the Compute Engine Service Account credentials for that VM.

See the [Application Default Credentials](https://github.com/googleapis/google-auth-library-java#google-auth-library-oauth2-http) documentation for how to enable other use cases.

Finally, the `GOOGLE_APPLICATION_CREDENTIALS` environment variable can be used to specify location of the Google credentials file.

If you don't have it, the credentials file can be downloaded from the Google Cloud Console following these steps:

- Open the [Google Cloud Console](https://console.cloud.google.com)
- Go to APIs & Services → Credentials
- Click on the *Create credentials* (blue) drop-down and choose *Service account key*, in the following page
- Select an existing *Service account* or create a new one if needed
- Select JSON as *Key type*
- Click the *Create* button and download the JSON file giving a name of your choice e.g. `creds.json`.

Then, define the following variable replacing the path in the example with the one of your credentials file just downloaded:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/your/file/creds.json"
```

See [Get started with Nextflow on Google Cloud Batch](https://www.nextflow.io/blog/2023/nextflow-with-gbatch.html) for more information on how to use Google Cloud Batch, 
including how to set the required roles for your service account.

(google-batch)=

## Cloud Batch

:::{versionadded} 22.07.1-edge
:::

[Google Cloud Batch](https://cloud.google.com/batch) is a managed computing service that allows the execution of containerized workloads in the Google Cloud Platform infrastructure.

Nextflow provides built-in support for Google Cloud Batch, allowing the seamless deployment of Nextflow pipelines in the cloud, in which tasks are offloaded to the Cloud Batch service.

Read the {ref}`Google Cloud Batch executor <google-batch-executor>` section to learn more about the `google-batch` executor in Nextflow.

(google-batch-config)=

### Configuration

Make sure to have defined in your environment the `GOOGLE_APPLICATION_CREDENTIALS` variable. See the [Credentials](#credentials) section for details.

:::{note}
Make sure your Google account is allowed to access the Google Cloud Batch service by checking the [APIs & Services](https://console.cloud.google.com/apis/dashboard) dashboard.
:::

Create or edit the file `nextflow.config` in your project root directory. The config must specify the following parameters:

- Google Cloud Batch as Nextflow executor
- The Docker container image(s) for pipeline tasks
- The Google Cloud project ID and location

Example:

```groovy
process {
    executor = 'google-batch'
    container = 'your/container:latest'
}

google {
    project = 'your-project-id'
    location = 'us-central1'
}
```

Notes:

- A container image must be specified to execute processes. You can use a different Docker image for each process using one or more {ref}`config-process-selectors`.
- Make sure to specify the project ID, not the project name.
- Make sure to specify a location where Google Batch is available. Refer to the [Google Batch documentation](https://cloud.google.com/batch/docs/get-started#locations) for region availability.

Read the {ref}`Google configuration<config-google>` section to learn more about advanced configuration options.

(google-batch-process)=

### Process definition

By default, the `cpus` and `memory` directives are used to find the cheapest machine type that is available at the current
location and that fits the requested resources. If `memory` is not specified, 1 GB of memory is allocated per CPU.

The `machineType` directive can be used to request a specific VM instance type. It can be any predefined Google Compute
Platform [machine type](https://cloud.google.com/compute/docs/machine-types) or [custom machine type](https://cloud.google.com/compute/docs/instances/creating-instance-with-custom-machine-type).

```nextflow
process my_task {
    cpus 8
    memory '40 GB'

    script:
    """
    your_command --here
    """
}

process other_task {
    machineType 'n1-highmem-8'

    script:
    """
    your_command --here
    """
}
```

:::{versionadded} 23.02.0-edge
:::

The `machineType` directive can also be a comma-separated list of patterns. The pattern can contain a `*` to match any
number of characters and `?` to match any single character. Examples of valid patterns: `c2-*`, `m?-standard*`, `n*`.

```nextflow
process my_task {
    cpus 8
    memory '20 GB'
    machineType 'n2-*,c2-*,m3-*'

    script:
    """
    your_command --here
    """
}
```

:::{versionadded} 23.12.0-edge
:::

The `machineType` directive can also be an [Instance Template](https://cloud.google.com/compute/docs/instance-templates),
specified as `template://<instance-template>`. For example:

```nextflow
process my_task {
    cpus 8
    memory '20 GB'
    machineType 'template://my-template'

    script:
    """
    your_command --here
    """
}
```

:::{note}
Using an instance template will overwrite the `accelerator` and `disk` directives, as well as the following Google Batch
config options: `bootDiskImage`, `cpuPlatform`, `preemptible`, and `spot`.
:::

To use an instance template with GPUs, you must also set the `google.batch.installGpuDrivers` config option to `true`.

To use an instance template with Fusion, the instance template must include a `local-ssd` disk named `fusion` with 375 GB.
See the [Google Batch documentation](https://cloud.google.com/compute/docs/disks/local-ssd) for more details about local SSDs.


:::{versionadded} 23.06.0-edge
:::

The `disk` directive can be used to set the boot disk size or provision a disk for scratch storage. If the disk type is specified with the `type` option, a new disk will be mounted to the task VM at `/tmp` with the requested size and type. Otherwise, it will set the boot disk size, overriding the `google.batch.bootDiskSize` config option. See the [Google Batch documentation](https://cloud.google.com/compute/docs/disks) for more information about the available disk types.

Examples:

```nextflow
// set the boot disk size
disk 100.GB

// mount a persistent disk at '/tmp'
disk 100.GB, type: 'pd-standard'

// mount a local SSD disk at '/tmp' (should be a multiple of 375 GB)
disk 375.GB, type: 'local-ssd'
```

### Pipeline execution

The pipeline can be launched either in a local computer or a cloud instance. Pipeline input data can be stored either locally or in a Google Storage bucket.

The pipeline execution must specify a Google Storage bucket where the workflow's intermediate results are stored using the `-work-dir` command line options. For example:

```bash
nextflow run <script or project name> -work-dir gs://my-bucket/some/path
```

:::{tip}
Any input data **not** stored in a Google Storage bucket will automatically be transferred to the pipeline work bucket. Use this feature with caution being careful to avoid unnecessary data transfers.
:::

:::{warning}
The Google Storage path needs to contain at least sub-directory. Don't use only the bucket name e.g. `gs://my-bucket`.
:::

### Spot Instances

Spot Instances are supported by adding the following setting in the Nextflow config file:

```groovy
google {
    batch.spot = true
}
```

:::{versionadded} 23.11.0-edge
:::

Since this type of virtual machines can be retired by the provider before the job completion, it is advisable to add the following retry strategy to your config file to instruct Nextflow to automatically re-execute a job if the virtual machine was terminated preemptively:

```groovy
process {
    errorStrategy = { task.exitStatus==50001 ? 'retry' : 'terminate' }
    maxRetries = 5
}
```

### Fusion file system

:::{versionadded} 23.02.0-edge
:::

The Google Batch executor supports the use of {ref}`fusion-page`. Fusion allows the use of Google Cloud Storage as a virtual distributed file system, optimizing the data transfer and speeding up most job I/O operations.

See [Google Cloud Batch](https://docs.seqera.io/fusion/guide/gcp-batch) for more information about configuring Fusion for Google Cloud Batch.

### Supported directives

Currently, the following Nextflow directives are supported by the Google Batch executor:

- {ref}`process-accelerator`
- {ref}`process-container`
- {ref}`process-containeroptions`
- {ref}`process-cpus`
- {ref}`process-disk`
- {ref}`process-executor`
- {ref}`process-machinetype`
- {ref}`process-memory`
- {ref}`process-time`

### Hybrid execution

Nextflow allows the use of multiple executors in the same workflow. This feature enables the deployment of hybrid workloads, in which some jobs are executed in the local computer or local computing cluster, and some jobs are offloaded to Google Cloud.

To enable this feature, use one or more {ref}`config-process-selectors` in your Nextflow configuration file to apply the Google Cloud executor to the subset of processes that you want to offload. For example:

```groovy
process {
    withLabel: bigTask {
        executor = 'google-batch'
        container = 'my/image:tag'
    }
}

google {
    project = 'your-project-id'
    location = 'us-central1' // for Google Batch
}
```

Then launch the pipeline with the `-bucket-dir` option to specify a Google Storage path for the jobs computed with Google Cloud and, optionally, the `-work-dir` to specify the local storage for the jobs computed locally:

```bash
nextflow run <script or project name> -bucket-dir gs://my-bucket/some/path
```

:::{warning}
The Google Storage path needs to contain at least one sub-directory (e.g. `gs://my-bucket/work` rather than `gs://my-bucket`).
:::

:::{note}
Nextflow will automatically manage the transfer of input and output files between the local and cloud environments when using hybrid workloads.
:::

### Limitations

- Compute resources in Google Cloud are subject to [resource quotas](https://cloud.google.com/compute/quotas), which may affect your ability to run pipelines at scale. You can request quota increases, and your quotas may automatically increase over time as you use the platform. In particular, GPU quotas are initially set to 0, so you must explicitly request a quota increase in order to use GPUs. You can initially request an increase to 1 GPU at a time, and after one billing cycle you may be able to increase it further.

- Currently, it's not possible to specify a disk type different from the default one assigned by the service depending on the chosen instance type.

