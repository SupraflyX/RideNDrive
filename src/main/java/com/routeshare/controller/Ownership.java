package com.routeshare.controller;

/* the "you can only change your own stuff" check, in one place so every endpoint does it
   the same way. the caller says who they are with actorId, and a failed check throws
   SecurityException, which comes back as a 403.

   this is ownership, not authentication: nothing here proves the caller really is the
   actorId they claim. real sessions or tokens would fix that, and because the check lives
   in one file, only this would have to change. */
final class Ownership {

    private Ownership() {
    }

    // ownerId null means nobody owns it, which also fails
    static void require(Long ownerId, Long actorId, String what) {
        if (ownerId == null || actorId == null || !ownerId.equals(actorId)) {
            throw new SecurityException("Only the owner of this " + what + " may change it.");
        }
    }
}
