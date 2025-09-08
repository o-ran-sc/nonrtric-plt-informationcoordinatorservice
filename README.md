# Data Management and Exposure API: Information Coordination Service (ICS) (Experimental O-RAN-SC Module)

![Status: Not for Production](https://img.shields.io/badge/status-not--for--production-red)
![Status: Experimental](https://img.shields.io/badge/CVE%20Support-none-lightgrey)

> [!WARNING]
> This repository is pre-spec and not intended for production use. No CVE remediation or production guarantees apply.

## Overview

The Information Coordination Service (ICS) is a generic service designed to manage data subscriptions in a multi-vendor environment. It facilitates decoupling between data consumers and producers, allowing seamless interaction without consumers needing knowledge of specific producers. 

## Key Concepts

- **Data Consumer**: Subscribes to data by creating an "Information Job". Examples include R-Apps using the R1 API or NearRT-RIC using the A1-EI API.

- **Information Type**: Defines the interface between consumers and producers, specifying parameters for subscription creation via a JSON schema. Parameters include data delivery details, filtering criteria, periodicity, and aggregation information.

- **Data Producer**: Generates data and receives notifications about relevant information jobs. Filtering ideally occurs at the producer's source.

## Persistence and Security

Information Jobs and types are persistently stored using Amazon S3 or a local file system. Fine-grained access control supports secure data consumption by enforcing access checks during job modifications or reads. External authorization services like Open Policy Agent (OPA) can be integrated for access token-based authorization.

## Requirements

- Support for multiple data producers, potentially from different vendors, producing the same data type.

- Flexible installation, upgrade, scaling, and restarting of software components independently.

- Decoupling of data producers from consumers, ensuring consumer operations remain unaffected by producer changes.

- Ability for consumers to initiate subscriptions independently of producer status changes (e.g., upgrades, restarts).

## Principles

- ICS manages data subscriptions but doesn't handle data delivery protocols, enabling flexibility in implementation.

- Data filtering is managed by producers, allowing unrestricted methods for data selection.

- Consumers create subscriptions regardless of producer status, ensuring continuous service without consumer intervention.

## Implementation Details

- **Language**: Java
- **Framework**: Spring Boot

## Configuration

Configuration details can be found in the standard `application.yaml` file.

## Documentation

For detailed API documentation and further information, refer to the NONRTRIC documentation at [NONRTRIC Documentation](https://docs.o-ran-sc.org/projects/o-ran-sc-nonrtric/en/latest/overview.html#information-coordination-service).

## License

Copyright (C) 2024 Nordix Foundation. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
