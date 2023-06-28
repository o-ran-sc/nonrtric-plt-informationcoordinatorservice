.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. SPDX-License-Identifier: CC-BY-4.0
.. Copyright (C) 2021-2023 Nordix

Information Coordination Service
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Information Coordination Service (ICS) is a generic service that maintains data subscriptions. Its main purpose is
to decouple data consumers and data producers in a multi vendor environment. A data consumer does not need to know anything about
the producers of the data.

This product is a part of :doc:`NONRTRIC <nonrtric:index>`.

The following terms are used:

* **Data Consumer**, is a subscriber of data. Subscription is done by creating an "Information Job". A data consumer can for instance be an R-App (using the R1 API) or a NearRT-RIC consuming Enrichment Information (and uses the A1-EI API provided by this service).
* **Information Type**, is a type of information. This defines an interface between consumers and producers. Each information type defines which parameters can be used for creating an information job. These parameters are defined by a Json schema connected to the Information Type. These parameters may include:

  * Parameters related to data delivery (for instance a callback URL if REST is used or a Kafka stream). These are different for different delivery protocols.
  * Filtering or other information for data selection.
  * Periodicy
  * Other info used for aggregation

* **Data Producer** is a producer of data. A producer will get notified about all information jobs of its supported types. This also means that filtering is done at the producer (ideally at the source of the data). A data producer can for instance be an R-App.

One information type can be supported by zero to many data producers and can be subscribed to by zero to many data consumers. There can be two data producers for a type of data; one from one vendor (handling a part of the network) and another from a different vendor. A data consumer is agnostic about this.

.. image:: ./Architecture.png
   :width: 500pt

Information Jobs and types are stored persistently by ICS in a local database. This can be either using Amazon S3 - Cloud Object Storage or file system.

To restrict which data that can be consumed and by whom there is support for finegrained access control. When data subscriptions/jobs are modified or read, an access check can be performed.
ICS can be configured to call an external authorizer.
This can be Open Policy Agent (OPA) which can grant or deny accesses based on an access token (JWT) used by the calling data consumer.
In addition to this the information type, accesstype (read/write) and all type specific parameters can be used by access rules.

The URL to the authorization component is defined in the application.yaml file and the call invoked to by ICS is described in API documentation.


****************
Key Requirements
****************

* Multiple producers, posibly from different vendors, should be able to
  produce the same type of data.

* The system should allow for arbitrary installation, upgrade, scaling
  up, scaling down, and restarting of software, in any order.

* ICS should decouple data producers from data consumers. Consumers
  should not be aware of producers, their endpoints, or the number of
  producers. Upgrades, scaling, and other changes to producers should not
  affect consumers. That also means that a producer can be replaced by
  another one.

* Data consumers should be able to start subscriptions at any time,
  including before a producer is installed, or during a producer's
  upgrade, restart, or scaling up/down.

* The system should impose as few restrictions as possible on how
  software is implemented. Applications may, for example, be implemented
  as multiple identical executing entities for load sharing.

* The lifecycle of a data type in the system should not be controlled
  by a single producer. There may be data types and subscriptions for
  data types that have no producers, such as during an upgrade scenario.

* A data type specification should contain all the information needed
  to implement both producers and consumers. The data type specification
  forms the API between these. It must define the ID of the data type,
  all possible parameters used at subscription creation (such as filter
  constructions), and all details about the delivered format and the
  delivery mechanism. This is comparable to a Managed Object Model.

* ICS should mediate data type versioning and choose compatible data
  producers. For example, if there are two producers, one producing
  dataType version 1.5.0 and another producing version 1.4.0, and a
  consumer subscribes to data version 1.1.0, both these producers should
  be involved (since version 1.4.0 and version 1.5.0 are backwards
  compatible with version 1.1.0). The system should be able to handle
  these versioning scenarios automatically.

* A consumer shall be able to retrieve its jobs after a restart. Therefore it must be possible
  to group the jobs based on a label "owner" which is defined by the consumer. This must be unique,
  which suggests that it should be based on UUIDS, but this is up to the consumer.
  This "owner" can be a POD, an application etc. ICS should not restrict that.

*********************
Summary of principles
*********************

* ICS provides APIs for control of data subscriptions, but is not involved in the delivery of data. This means that any delivery protocol can be used.
* Data for one Information type can be produced by many producers from different vendors.
* Data filtering is done by the producer. ICS does not restrict how data selection/filtering is done.
* A Data Consumer can create a data subscription (Information Job) regardless of the status of the data producers. The producers can come and go without any need for the Data Consumer to take any action.
  A subscription indicates the need for a type of data and the system should do its best to fulfill this.
* ICS is by design not aware of any subscribable data types.
* When a consumer creates a subscription/job, ICS shall choose the information type version with the lowest available
  compatible version. All producers that have registered a type that is compatible with the chosen version are included.
  Example, if a consumer creates a job with type version 1.1.0, the chosen type may 1.2.0 and a producer
  supporting version 1.9.0 will be included (but not a producer that supports version 2.0.0).

*******
Example
*******

.. image:: ./Example.png
   :width: 500pt

In the example, there is one subscription and the type of data is supported by two producers. That means that both producers are aware of the information job and will delver data directly to the subscriber.

So a typical sequence is that:

* An Information Type is registered.
* Producers of the Information Types are registered
* A Consumer creates an Information Job of the type and supplies the type specific parameters for data delivery and filtering etc.
* The producers gets notified of the job and will start producing data.

If a new producer is started, it will register itself and will get notified of all jobs of its supported types.


**************
Implementation
**************

Implemented as a Java Spring Boot application.

*************
Configuration
*************

The component is configured by the usual spring boot application.yaml file.

An example application.yaml configuration file: ":download:`link <../config/application.yaml>`"

