from __future__ import annotations

import unittest

from connexa_ingestion.attachments import AttachmentPolicy
from connexa_ingestion.models import AttachmentMetadata


class AttachmentPolicyTests(unittest.TestCase):
    def test_attachments_are_disabled_by_default(self) -> None:
        attachment = AttachmentMetadata("agenda.pdf", "application/pdf", 512)

        decision = AttachmentPolicy().evaluate(attachment)

        self.assertFalse(decision.accepted)

    def test_policy_rejects_path_like_filename(self) -> None:
        attachment = AttachmentMetadata("../agenda.pdf", "application/pdf", 512)

        decision = AttachmentPolicy(enabled=True).evaluate(attachment)

        self.assertFalse(decision.accepted)
        self.assertEqual(decision.reason, "attachment filename is unsafe")

    def test_policy_accepts_a_small_allow_listed_attachment(self) -> None:
        attachment = AttachmentMetadata("agenda.pdf", "application/pdf", 512)

        decision = AttachmentPolicy(enabled=True).evaluate(attachment)

        self.assertTrue(decision.accepted)
        self.assertEqual(decision.sanitized_filename, "agenda.pdf")


if __name__ == "__main__":
    unittest.main()
