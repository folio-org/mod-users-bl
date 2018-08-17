## 4.0.0 2018-08-18
 * Add support for associated service points with user record

## 3.0.0 2018-05-31
 * Update RAML definitions (meta removed from proxyFor)
 * Modify module descriptor to allow modusers v15.0

## 2.2.2 2018-05-11
 * Upgrade to raml-module-builder 19.1.0
 * Expand testing infrastructure to allow for more comprehensive unit tests to be written
 * Correct template bug for retrieving expanded permissions

## 2.2.1
 * Update shared raml to ensure consistent schema declarations in RAML

## 2.0.3 2018-01-15
 * Update RAML definitions to recognize metadata field in users

## 2.0.2 2017-08-25
 * Patch RAML to allow for proxyFor field in users

## 2.0.1 2017-07-16
 * Remove redundant permissions from ModuleDescriptor

## 2.0.0 2017-07-16
 * Port module to RAML-Module-Builder Framework
 * Implement new composite object return format
 * Implement "includes" parameter
 * Allow mod-user v14 in requires
 * Add version string into Module Descriptor id
 * MODUSERBL-15 /bl-users/login response contains incorrect set of permissions

## 1.0.4 2017-06-26
 * Require mod-user version 13
 * Correct desired field for users record count

## 1.0.3 2017-06-22
 * Add usergroups.collection.get permission to permission set

## 2017-06-08
 * Update ModuleDescriptor to use v11 of mod-users
 * Implement expandPermissions flag for requests
 * Add users-bl.all permission (set to hidden)
 * Update submodule path to use https instead of ssh

## 2017-06-07
 * Change response format to match id/object format
 * Fix bug in Group lookup
 * Add /login endpoint

## 2017-05-31
 * Change ModuleDescriptor to set visibility of selection permissions

## 2017-05-26
 * Update to use version 10 of mod-users, version 4 of mod-permissions

## 2017-05-12
 * Update to use version 9 of mod-users and version 3 of mod-login and mod-permissions

