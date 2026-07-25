package com.routeshare.controller;

/**
 * Ownership centralises the "you may only change your own records" check that the
 * mutating REST endpoints apply.
 *
 * The platform identifies the acting user by an {@code actorId} request parameter —
 * the same convention BookingController has used since Sprint 9. A failed check raises
 * SecurityException, which GlobalExceptionHandler renders as HTTP 403.
 *
 * SCOPE — this is ownership enforcement, not authentication. Nothing yet proves that
 * the caller *is* the actor they claim to be; a client can still assert any actorId.
 * Closing that requires real authentication (a session or signed token verified
 * server-side), which is a separate piece of work. What this class does buy is that
 * every mutating endpoint now agrees on who is allowed to touch what, so adding real
 * identity later means changing how `actorId` is obtained — in one place — rather than
 * introducing the concept of an owner across a dozen endpoints.
 */
final class Ownership {

    private Ownership() {
    }

    /**
     * Requires that the acting user is the owner of the record being changed.
     *
     * @param ownerId the id of the user who owns the record (null means unowned)
     * @param actorId the id supplied by the caller
     * @param what    human-readable subject, used in the 403 message
     */
    static void require(Long ownerId, Long actorId, String what) {
        if (ownerId == null || actorId == null || !ownerId.equals(actorId)) {
            throw new SecurityException("Only the owner of this " + what + " may change it.");
        }
    }
}
