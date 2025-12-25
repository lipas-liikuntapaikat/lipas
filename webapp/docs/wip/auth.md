# Authentication & Authorization

> **Status**: Draft needed

## Topics to Cover

- [ ] JWT token flow (issue, validate, refresh)
- [ ] Login/logout process
- [ ] Role system (`lipas.roles`)
- [ ] Permission checks (`check-privilege`)
- [ ] City-based permissions
- [ ] Admin vs regular user capabilities
- [ ] API authentication

## Key Files

- `src/clj/lipas/backend/jwt.clj`
- `src/clj/lipas/backend/auth.clj`
- `src/cljc/lipas/roles.cljc`
- `src/clj/lipas/backend/middleware.clj`

## Roles

| Role | Description |
|------|-------------|
| `:admin` | Full system access |
| ... | ... |

## Notes

<!-- Add notes here as you explore -->
