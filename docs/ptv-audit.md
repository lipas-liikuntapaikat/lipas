# PTV Audit

User with :ptv/audit role can provide feedback on PTV data (summary, description). They can choose an organization and see their data that is synced to PTV. They can provide feedback on each sports facility's summary and description texts and label them either "approved" or "changes-requested". Audit information is persisted by appending it as part of ptv data in Lipas. Persistend information includes the feedback, timestamp and auditor's user id.

User with :ptv/manage role can see audit results for organizations where they have permissions to. Change requests are highlighted and the user is guided to make the requested changes.
