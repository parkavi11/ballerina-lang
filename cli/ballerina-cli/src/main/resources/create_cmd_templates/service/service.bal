import ballerina/http;

# A service representing a network-accessible API
# bound to port `9090`.
service hello on new http:Listener(9090) {

    # A resource respresenting an invokable API method
    # accessible at `/hello/sayHello`.
    # add return
    resource function sayHello(string name) returns string {
        // Send a response back to the caller.

        if (name.isBlank()) {
            return "Hello, World!";
        } else {
            return "Hello, " + name;
        }
    }
}
