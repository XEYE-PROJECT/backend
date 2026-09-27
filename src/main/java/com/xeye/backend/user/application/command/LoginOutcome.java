package com.xeye.backend.user.application.command;

/** Resultado de un login: sesión completa o reto de segundo factor. */
public sealed interface LoginOutcome permits AuthResult, MfaChallenge {
}
