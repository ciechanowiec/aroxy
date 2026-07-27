package eu.ciechanowiec.aroxy.proxy;

/**
 * The lifecycle shared by the JDK HTTP/1.1 listener and Cursor's HTTP/1.1 + HTTP/2 listener.
 */
interface ProxyListener {

    void start();

    int port();

    void stop(int delaySeconds);
}
