# Change notes

One file per change to a published module, written by whoever made the change:

```markdown
---
level: patch            # patch | minor (pre-1.0: a minor is breaking)
modules: [stapel-auth, stapel-auth-ui]
---
Auth: OTP resend timer survives process death.
```

The release train consumes them (`stapelVersion`, see docs/release.md). The
automated checks (`checkChangeNote`, `checkAbiLevel`) are not wired yet.
