# Firebase integration boundary

Firebase is intentionally not configured in the foundation milestone. No `google-services.json`, service-account data, project ID, or access token belongs in this repository.

When the integration milestone begins, configuration will be supplied through the approved secret-management path and verified first against a non-production environment. Android code will continue to use the API boundary rather than direct privileged service access.
