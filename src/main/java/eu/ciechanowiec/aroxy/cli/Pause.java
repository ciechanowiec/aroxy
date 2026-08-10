package eu.ciechanowiec.aroxy.cli;

/**
 * Interruptible pause between log polling attempts.
 */
@FunctionalInterface
interface Pause {

    void run() throws InterruptedException;
}
