# mod-users-bl

Copyright (C) 2017-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Business logic "join" module to provide simple access to all user-centric data.

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

See project [MODUSERBL](https://issues.folio.org/browse/MODUSERBL)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)
