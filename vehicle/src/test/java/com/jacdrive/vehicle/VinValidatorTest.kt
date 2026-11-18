package com.jacdrive.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinValidatorTest {
    @Test fun acceptsWellFormedVin() = assertTrue(VinValidator.isValid("LJ12EKS17N4000001"))
    @Test fun rejectsForbiddenLetters() = assertFalse(VinValidator.isValid("LJ12EKS17N400000I"))
    @Test fun rejectsWrongLength() = assertFalse(VinValidator.isValid("LJ12EKS17"))
}
