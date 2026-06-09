# LIPAS Organizations

## What this is

LIPAS lets municipalities, consortia, the state, companies and sports
federations be represented as **organizations**. An organization is a named
group of people that can own sports facilities, decide who may edit them, and
share its contact information and instructions with its members.

The goal is to let an organization run its own affairs — manage its people, look
after its facilities, and collaborate with other organizations — without a LIPAS
administrator having to step in for routine work.

Organizations are **opt-in and additive**. A user who belongs to no organization
keeps working exactly as before; nothing about the existing login or editing
experience changes for them. Everything described here only comes into play once
someone is made a member of an organization.

## Two ways permissions are granted

LIPAS now has two clear, independent ways a person can gain editing rights:

1. **Direct permissions** — the long-standing system where a LIPAS administrator
   grants an individual the right to edit, say, a particular municipality's
   facilities or a particular activity. These are managed in the admin user-
   management screen and are unchanged.

2. **Organization membership** — the new system described here. Being a member of
   an organization, and the roles you are given inside it, determine what you can
   do on that organization's behalf.

The two planes don't interfere with each other. Org-related rights come *only*
from membership; they are never hand-assigned to an individual account.

## What an organization holds

Each organization keeps:

- **A name and a type** — city, municipal consortium, state, private company, or
  sports federation. The type reflects what kind of body the organization is and
  influences some defaults (for example, the owner label applied to its
  facilities).
- **Primary contact information** — phone, email, website and a reservations link,
  maintained in one place.
- **Instructions** for its members, written in Finnish, Swedish and English. Org
  administrators write them; members read them. This is typically the first thing
  a member sees.
- **A catalog of roles** that can be assigned to members (see below).
- **An ownership rule** describing which facilities the organization may claim as
  its own.
- **A list of members**, each with the roles they have been given.

## Roles inside an organization

Each member of an organization holds a single, flat list of **roles**. There are
no longer separate "kinds" of membership to juggle — earlier versions had two
special built-in roles (one for plain membership, one for organization
administration) layered on top of editing rights; those have been collapsed into
this one list.

Almost every role a member can hold comes from the organization's **role catalog**,
which only a LIPAS administrator can define. There are just two things that don't:

- **The view baseline** — being a member *at all* automatically grants the right to
  view the organization (its details, facilities and roster). Nobody assigns it; it
  comes free with membership, and it's why a member with no other roles can still
  see their organization.
- **Ylläpitäjä (Administrator)** — the management role, which lets a member edit the
  organization's details, contact info and instructions, manage members and their
  roles, and share editing rights on the organization's facilities. This role is
  **reserved** — a LIPAS administrator or an organization administrator can *assign*
  it to a member, but it is deliberately kept *outside* the editable catalog so that
  editing the catalog can never accidentally strip an organization of all its
  administrators.

Everything else — the **data-editing roles** — comes from the catalog. These let a
member edit facility data on the organization's behalf, and are assembled from a
palette of role types, each scoped to what it applies to: editing this
organization's facilities, the facilities of a particular municipality or facility
type, specific named facilities, outdoor-recreation (UTP) data, PTV data, and so on.

The catalog acts as a **ceiling**: an organization's administrators can freely
assign the roles in it, but only a LIPAS administrator can define or change the
catalog itself. This means an organization can manage its own people day to day,
while LIPAS administration retains control over how much power those roles can ever
confer.

To make that control transparent, every role in the catalog **describes itself**:
each one carries a plain-language summary and a spelled-out list of the exact
access rights it includes. So whoever assembles the catalog — and whoever assigns
from it — can see what a role grants without having to remember it, and no role is
ever shown as an unexplained or unknown permission. (The same self-describing role
form is reused in the direct-permissions admin screen.)

## Facilities: ownership and shared editing

Facilities can now be connected to organizations in two ways:

- **Ownership** — a facility can be owned by one organization. Members of the
  owning organization with an editing role can edit it.
- **Shared editing (edit grants)** — the owning organization can grant another
  organization the right to edit a facility, enabling cross-organization
  collaboration without transferring ownership.

This makes two everyday questions easy to answer:

- **"Which facilities does this organization own (or may edit)?"** — shown in the
  organization's own facilities view.
- **"Who can edit this facility?"** — shown per facility, listing the owning
  organization, any organizations it has been shared with, organizations that can
  edit through a relevant activity, and any individuals who hold a direct
  permission. Each entry explains *why* that party can edit.

Ownership and shared-editing changes are security-sensitive, so they are guarded
carefully: only a LIPAS administrator can change who owns an existing facility,
and only the owning organization's administrator (or a LIPAS administrator) can
change who it is shared with.

## Claiming ownership of facilities

An organization can take ownership of facilities that match its ownership rule —
for example, all the sports facilities in a particular municipality. Because this
affects many records at once, the flow always shows a concrete preview (how many
facilities, which ones) before anything happens. A LIPAS administrator can apply
such a claim directly; an organization administrator's request goes into an
approval queue that a LIPAS administrator reviews.

## Setting ownership when creating a facility

When someone who belongs to an owner-capable organization creates a new facility,
the form offers to set the owning organization, pre-filling it when there's an
obvious choice. Choosing an organization also aligns the facility's owner label
with the organization's type. The choice can be cleared to record a non-
organization (legacy) owner instead — useful, for instance, when a municipal
worker maintains a privately owned rink.

## The organization screens

Organizations are managed from a single set of screens. LIPAS administrators and
organization administrators use the *same* interface — controls simply appear or
become editable depending on what the viewer is allowed to do. The screens are
organized into tabs:

- **Ohjeet (Instructions)** — member-facing guidance, in three languages.
- **Kohteet (Facilities)** — the organization's facilities (owned and shared),
  with filtering, the per-facility "who can edit" view and its recent edit
  history, bulk editing, sharing of editing rights, and ownership claims.
- **Yleistiedot (Overview)** — name, type and contact information.
- **Jäsenet (Members)** — the roster, inviting new members, and assigning roles.
- **Käyttöoikeudet (Access rights)** — the role catalog, shown to everyone and
  editable only by LIPAS administrators.
- **PTV** — integration settings (see below).
- **Historia (History)** — an audit timeline, visible to administrators only.

## Inviting and managing members

Members are added by email address. If the address already belongs to a LIPAS
account, the person is simply added; if not, an account is created on the fly and
the person receives a magic login link by email. During the invite, the inviter
can grant any roles up front — including making the person an administrator — or
none, in which case the person joins as a plain (view-only) member. Roles can be
changed or revoked later from the members list.

## Bulk operations

From the facilities view, an organization can update contact information across
many of its facilities at once, rather than editing each facility individually.
As with ownership claims, the affected set is made explicit before changes are
applied.

## History and audit

Every meaningful change is recorded as part of an append-only history — renames,
contact and instruction edits, catalog changes, members joining or leaving, role
assignments, ownership take-overs and transfers, and sharing or revoking of
editing rights. The organization's History tab presents this as a readable,
reverse-chronological timeline with the people involved shown by name rather than
internal identifiers. Facility ownership and sharing changes are likewise part of
each facility's own history.

## PTV integration

Organizations can be connected to PTV (Palvelutietovaranto), Finland's national
service registry. PTV settings live with the organization and are managed by LIPAS
administrators. The organization's contact information feeds the PTV services it
publishes.

## Design intent

A few principles shaped the feature and are worth keeping in mind when extending
it:

- **Membership is the single source of org permissions.** What a membership grants
  is worked out fresh each time a person logs in — there is no separately stored,
  derivable permission state that could drift out of sync. Change a role catalog or
  remove a member and the effect simply follows at the next login.
- **The role catalog is the ceiling.** Organizations self-serve within boundaries
  that only LIPAS administration can set.
- **History is built in, not bolted on.** Both organization changes and facility
  ownership/sharing changes ride existing append-only history, so the audit trail
  is a natural by-product rather than a separate mechanism.
- **Opt-in by construction.** The feature never alters how individuals or their
  direct permissions work; an organization simply references its members.

## Known open items

A handful of product decisions are intentionally still open, including: whether to
show how individuals' email addresses are presented in the "who can edit" lists
(currently administrators only, pending data-protection guidance); whether to give
organization administrators a self-service entry point for requesting ownership of
individual facilities; and how to handle very large organizations whose facility
lists exceed current display limits. These are tracked alongside the detailed
design notes.
