# mod-users-bl

Copyright (C) 2017-2021 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Prerequisites

* Java 11 JDK
* Maven 3.3.9

## Introduction

Business logic "join" module to provide simple access to all user-centric data.

## Dependencies
This module has both required and optional interface dependencies in its [module descriptor](descriptors/ModuleDescriptor-template.json). This allows operators to choose which dependent modules to install for the desired features of this module, and does not require that all dependent modules be installed in every FOLIO instance.

### Required dependencies
This module has a number of required dependencies (designated in the `requires` array of the module descriptor) to support core features such as authentication, authorization, permissions, and configuration. Operators can install mod-users-bl with the required modules and expect to be able to perform a successful login through the UI. If the required modules are installed, this module also supports password validation and reset via a POST request to the `settings/myprofile/password` endpoint should password reset be required.

### Optional dependencies
Service point and notification interfaces are optional dependencies since each requires a large number of dependent modules and it is often desireable to have a minimal system. Unless the optional dependent modules of the `service-points` and `service-points-users` interfaces are installed in the FOLIO instance, service points are not returned in the login response. The login response will not produce an HTTP error response if service points are missing however. Instead a partial login response (a partial `CompositeUser` object) is returned without the service points included. Okapi will log the 404 for the call to the service points endpoint letting the operator know that a dependency is missing for this feature, but that is all it will do.

If operators wish to support a password reset which involves sending a notification (via the `/password-reset/reset` endpoint), they should install the many dependent modules of the `notify` interfaces. However, password reset is still testable without `notify`. A request to the `password-reset/link` endpoint will produce a token (embedded in the link) which can then be provided to the `/validate` and `/reset` endpoints. Other endpoints in this module that rely on the `notify` interface will behave normally without `notify`, except that an error will be logged that `notify` could not be reached.

## Usage
The module exposes a number of endpoints to provide a composite object that links a given user record with a number of related records. All of the current methods are read-only. Creation and modification of composite records is planned for future versions.

#### `/bl-users/by-id/<id>`
* Description: Return a composite object referenced by the user's id
* Supported operations: GET
* queryParameters supported: expandPermissions
* Permissions required?: Yes


#### `/bl-users/by-username/<username>`
* Description: Return a composite object referenced by the user's username
* Supported operations: GET
* queryParameters supported: expandPermissions
* Permissions required?: Yes

#### `/bl-users/_self`
* Description: Return a composite object for the currently logged in user
* Supported operations: GET
* queryParameters supported: expandPermissions
* Permissions required?: No

#### `/bl-users/login`
* Description: Log a user in and return a composite object for that user, as well as the JWT
* Support operations: POST
* queryParameters supported: expandPermissions
* Permissions required?: No

#### `bl-users/settings/myprofile/password`
* Description: Validate and change user's password
* Support operations: POST
* queryParameters supported: No
* Permissions required?: No

#### `/bl-users/by-id/<id>/open-transactions`
* Description: Return an object listing number of open transactions that are associated to the user referenced by the user's id
* Supported operations: GET
* queryParameters supported: no
* Permissions required?: Yes

#### `/bl-users/by-username/<username>/open-transactions`
* Description: Return an object listing number of open transactions that are associated to the user referenced by the user's username
* Supported operations: GET
* queryParameters supported: no
* Permissions required?: Yes

#### `/bl-users/by-id/<id>`
* Description: Delete a user referenced by user's id after checking for open transactions. Deletion will be executed if and only if the user has no open transactions.
* Supported operations: DELETE
* queryParameters supported: no
* Permissions required?: Yes

## Object Format

The returned composite object format contains both ids and objects for the component records. The ids will always be populated (if they exist). The objects will be populated if possible. If not possible, they will be `null`.

Example:
~~~~
{
  "userId": "0002",
  "permissionsId": "shane",
  "groupId": "53d5c933-87e4-44ed-86f2-5eb4273c2ef5",
  "user": {
    "username": "shane",
    "id": "0002",
    "active": true,
    "patronGroup": "53d5c933-87e4-44ed-86f2-5eb4273c2ef5"
  },
  "permissions": {
    "username": "shane",
    "permissions": [
      "perms.users",
      "perms.permissions",
      "login",
      "users.read",
      "users.create",
      "users.edit",
      "users.delete",
      "usergroups.read",
      "usergroups.create",
      "usergroups.edit",
      "usergroups.delete",
      "users.read.basic",
      "users.item.get",
      "users.all",
      "users-bl.item.get",
      "login.all"
    ]
  },
  "group": {
    "group": "administrators",
    "desc": "Big Bosses",
    "id": "53d5c933-87e4-44ed-86f2-5eb4273c2ef5"
  }
}
~~~~

## Query Parameters
 * expandPermissions: Supply a boolean value to determine whether or not to expand the permissions listing in the permissions object section of the composite result.

## Additional information

Other [modules](https://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [MODUSERBL](https://issues.folio.org/browse/MODUSERBL)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-users-bl).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-users-bl).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-users-bl/).

