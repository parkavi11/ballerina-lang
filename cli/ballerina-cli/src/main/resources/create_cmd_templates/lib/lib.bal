# Prints `Hello` with input string name.

public function hello(string name) returns string {
    if (name is ("")) {
        return "Name is empty!";
    } else {
        return "Hello, " + name;
    }
}
