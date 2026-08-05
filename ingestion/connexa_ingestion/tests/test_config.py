from __future__ import annotations

import unittest

from connexa_ingestion.config import ConfigurationError, IngestionSettings


class IngestionSettingsTests(unittest.TestCase):
    def test_blank_environment_is_read_without_secret_defaults(self) -> None:
        settings = IngestionSettings.from_environment({})

        self.assertIsNone(settings.announcement_mailbox)
        self.assertFalse(settings.attachment_policy.enabled)
        self.assertIn(
            "CONNEXA_MAIL_PASSWORD", settings.missing_required_environment_variables
        )
        with self.assertRaises(ConfigurationError):
            settings.validate_for_execution()

    def test_configured_allow_list_accepts_only_approved_senders(self) -> None:
        settings = IngestionSettings.from_environment(
            {
                "CONNEXA_ANNOUNCEMENT_MAILBOX": "announcements@example.org",
                "CONNEXA_MAIL_USERNAME": "worker@example.org",
                "CONNEXA_MAIL_PASSWORD": "test-only-value",
                "CONNEXA_INGESTION_ALLOWED_DOMAINS": "example.org",
            }
        )

        settings.validate_for_execution()
        self.assertTrue(settings.is_allowed_sender("notice@example.org"))
        self.assertFalse(settings.is_allowed_sender("notice@unapproved.org"))


if __name__ == "__main__":
    unittest.main()
