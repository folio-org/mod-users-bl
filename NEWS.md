## 7.7.4 2024-07-04

* Vert.x 4.5.8 fixing Netty form POST OOM CVE-2024-29025 (MODUSERBL-195)

## 7.7.3 2024-05-27

* 500 response when calling GET /bl-users/by-username/{username} (MODUSERBL-190)

## 7.7.1 2024-05-21

* Missing interface dependencies in module descriptor (MODUSERBL-189)

## 7.7.0 2024-03-20

* Update 'feesfines' interface version to 19.0 (MODUSERBL-178)
* Remove duplicated code (MODUSERBL-170)
* mod-users-bl Quesnelia 2024 R1 - RMB v35.2.x update (MODUSERBL-187)

## 7.6.0 2023-10-12

* Module needs to respond 404 when 404 is received from /authn/login and /token endpoints (MODUSERBL-180)
* Needs to use new /token/sign endpoint of mod-at (MODUSERBL-176)
* Implement /login-with-expiry to expose login expiration to the UI (MODUSERBL-150)
* Add support of Forgot Password/Forgot Username for Consortia functionality (MODUSERBL-169)
* Update to Java 17 mod-users-bl (MODUSERBL-172)
* Add support of Reset Password action for Consortia functionality (MODUSERBL-167)
* Fetch home tenant during login and extend response with it (MODUSERBL-166)
* Update feesfines interface version to 18.0 (MODUSERBL-165)
* Update copyright year (FOLIO-1021)
* Use GitHub Workflows api-lint, api-schema-lint and api-doc (FOLIO-3678)

## 7.5.0 2023-02-13

* Added user data form (MODUSERBL-156)
* Corrections to RAML files and examples (MODUSERBL-161, MODUSERBL-160)

## 7.4.0 2022-10-18

* Upgrade users interface to 16.0 (MODUSERBL-154)
* Upgrade to RAML Module Builder 35.0.0 (MODUSERBL-158)
* Username CQL injection in POST /bl-users/login (MODUSERBL-157)

## 7.3.0 2022-06-13

* removed unused permissions (MODUSERBL-140)

## 7.2.0 2022-02-22

* Upgrade to RMB 33.2.4 and addressing log4j security vulverability (MODUSERBL-123, MODUSERBL-137)

## 7.1.0 2021-10-05

* Optionally requires `feesfines 16.3 or 17.0`

## 7.0.0 2021-06-15

* Checks for open transactions (e.g. loans, fines) when attempting to delete a user (MODUSERBL-116, MODUSERBL-115)
* `embed_postgres` command line option is no longer supported (MODUSERBL-119)
* Upgrades to RAML-Module-Builder 33.0.0 (MODUSERBL-119)
* Upgrades to Vert.x 4.1.0.CR1 (MODUSERBL-119)
* Provides `users-bl 6.1`
* Optionally requires `loan-storage 6.1`
* Optionally requires `feesfines 16.3`

## 6.2.0 2021-03-11

* Upgrades to RAML-Module-Builder 32.1.0 (MODUSERBL-112)
* Upgrades to Vert.x 4.0.0 (MODUSERBL-112)

## 6.1.0 2020-10-07

* Password reset links cannot expire more than 4 weeks in the future (MODUSERBL-99)
* Password reset request succeeds even when sending email fails (MODUSERBL-100)
* Allows additional properties in user representation (MODUSERBL-101)
* Responds with up to 1000 service desks (MODUSERBL-95)
* Requires JDK 11 (MODUSERBL-106)
* Upgraded to RAML Module Builder 31.1.0 (MODUSERBL-104)

## 6.0.0 2020-06-11

* Includes link expiration in password reset e-mail (MODUSERBL-78)
* Removes the ability to retrieve user credentials (MODUSERBL-97)
* Allows additional properties in users JSON representation (MODUSERBL-89)
* Upgrades to RAML Module Builder 30.0.0 (MODUSERBL-90)
* Provides `users-bl 6.0`
* Requires `login 6.0 or 7.0`

## 5.3.0 2020-03-11

* Upgrades to RAML Module Builder 29.3.0 (MODUSERBL-83, MODUSERBL-84)

## 5.2.0 2019-12-04

 * [MODUSERBL-77](https://issues.folio.org/browse/MODUSERBL-77) [#83](https://github.com/folio-org/mod-users-bl/pull/83) [#86](https://github.com/folio-org/mod-users-bl/pull/86) Username with "\_" or space is treated incorrectly
 * [FOLIO-2321](https://issues.folio.org/browse/FOLIO-2321) [#88](https://github.com/folio-org/mod-users-bl/pull/88) Remove old ModuleDescriptor "metadata" section for each back-end module
 * [FOLIO-2358](https://issues.folio.org/browse/FOLIO-2358) [#89](https://github.com/folio-org/mod-users-bl/pull/89) Use JVM features (UseContainerSupport, MaxRAMPercentage) to manage container memory, switch to alpine-jre-openjdk8 Docker container

## 5.1.0 2019-09-25
 * MODUSERBL-75 Update raml-util submodule making metadata.createdByUserId
   optional
 * MODUSERBL-79 Update to RMB 25.0.2 (FOLIO-2282)

## 5.0.0 2019-07-24
 * MODUSERBL-73 Update API contract to implement a user password reset by link
 * MODUSERBL-72 Local Password Management
 * FOLIO-473 add links to README additional info
 * FOLIO-2003 initial module metadata, use containerMemory as for folio-ansible

## 4.4.0 2019-03-15
 * MODUSERBL-61	Forgot username/password - update default search fiels
 * MODUSERBL-62	Security: handling HTTP 422 errors
 * MODUSERBL-63	Forward request headers to mod-login
 * MODUSERBL-64	Add staffSlips array to servicepoint.json
 * MODUSERBL-66	Reset password by link does not work
 * MODUSERBL-67	Temporarily Allow Additional Properties in Service Points

## 4.3.2 2018-12-14
 * Fix issue with logs dumping sensitive information (MODUSERBL-35)
 * Don't populate tests until mock verticle has deployed (MODUSERBL-57)
 * Upgrade to RMB 23.2.1 (MODUSERBL-59)

## 4.3.1 2018-12-04
 * Add functionality to provide links for creation and reset of user passwords (MODUSERBL50,51,52,53)
 * Add endpoint POST /settings/myprofile/password for self-change of password (MODUSERBL-43)
 * Add functionality of sending notification to /bl-users/forgotten/password and /bl-users/forgotten/username (MODUSERBL-41)
 * New endpoint POST /bl-users/password-reset/reset for resetting password by the link (MODUSERBL-41)
 * New endpoint POST /bl-users/password-reset/validate to validate create/reset password link and log user into system to change password (MODUSERBL-40)
 * New endpoint POST /bl-users/password-reset/link. (MODUSERBL-39)
 * Update descriptor to be compatible with servicepoints 3.0 interface (MODUSERBL-45)
 * Change user's password endpoint was added (MODUSERBL-43)
 * Update RAML to 1.0 (MODUSERBL-38)

## 4.0.3 2018-08-31
 * postBlUsersForgottenPassword & postBlUsersForgottenUsername implementation

## 4.0.2 2018-08-20
 * Apply service point user bugfix to login logic

## 4.0.1 2018-08-20
 * Fix bug involving service points users with no service points

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

