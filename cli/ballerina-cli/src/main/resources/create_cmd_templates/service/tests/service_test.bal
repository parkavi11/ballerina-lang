import ballerina/io;
import ballerina/test;

http:Client testClient = check new ("http://localhost:9090/hello");

# Before Suite Function

@test:BeforeSuite
function beforeSuiteFunc() {
    io:println("I'm the before suite function!");
}

# Test function

@test:Config {}
function testServiceFunction() returns string {
    io:println("Do your service Test!");
    http:Response response = check testClient->get("/sayHello/?name=John");
    test:assertEquals("Hello, John");
}

# Negative test function

@test:Config {}
function testServiceFunctionNegative() returns error? {
    io:println("Do your negative service Test!");
    http:Response response = check testClient->get("/sayHello/?name=");
    test:assertEquals("Name is empty!");
}

# After Suite Function

@test:AfterSuite
function afterSuiteFunc() {
    io:println("I'm the after suite function!");
}