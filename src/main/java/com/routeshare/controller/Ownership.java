package com.routeshare.controller;

// the "you can only change your own stuff" check, in one place so every endpoint
// does it the same way. the caller says who they are with an actorId parameter, and
// a failed check throws SecurityException, which comes back as a 403.
//
// worth being clear about what this is not: it's ownership, not authentication.
// nothing here proves the caller really is the actorId they claim, so a client can
// still just send someone else's id. fixing that needs real login sessions or tokens
// checked on the server, which is its own job. what this does buy is that when that
// happens, only the way actorId is obtained has to change, in one file, instead of
// adding the whole idea of an owner to a dozen endpoints.
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
