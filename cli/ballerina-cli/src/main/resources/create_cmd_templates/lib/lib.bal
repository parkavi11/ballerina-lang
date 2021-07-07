# Prints `Hello` with input string name.

public function hello(string name) returns string {
    if (name.isBlank()) {
        return "Hello, World!";
    } else {
        return "Hello, " + name;
    }
}
