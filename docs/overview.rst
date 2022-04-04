.. This work is licensed under a Creative Commons Attribution 4.0 International License.
.. SPDX-License-Identifier: CC-BY-4.0
.. Copyright (C) 2021 Nordix

Information Coordination Service
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Coordinate/Register Information Types, Producers, Consumers, and Jobs.

Coordinate/Register A1-EI Types, Producers, Consumers, and Jobs (A1 Enrichment Information Job Coordination).

* Maintains a registry of:

  + Information Types / schemas
  + Information Producers
  + Information Consumers
  + Information Jobs

* Information Query API (e.g. per producer, per consumer, per types).
* Query status of Information jobs.
* After Information-type/Producer/Consumer/Job is successfully registered delivery/flow can happen directly between Information Producers and Information Consumers.
* The Information Coordinator Service natively supports the O-RAN A1 Enrichment Information (A1-EI) interface, supporting coordination A1-EI Jobs where information (A1-EI)flow from the SMO/Non-RT-RIC/rApps to near-RT-RICs over the A1 interface.

Implementation:

* Implemented as a Java Spring Boot application.
