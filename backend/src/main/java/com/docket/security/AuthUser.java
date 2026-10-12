package com.docket.security;

/** The authenticated principal: who they are and which login session (token family) they are using. */
public record AuthUser(long userId, String sid) {}
