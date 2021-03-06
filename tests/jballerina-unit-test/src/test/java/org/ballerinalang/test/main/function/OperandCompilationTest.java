/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.test.main.function;

import org.ballerinalang.test.BCompileUtil;
import org.ballerinalang.test.CompileResult;
import org.testng.annotations.Test;

import static org.ballerinalang.test.BAssertUtil.validateError;
import static org.testng.Assert.assertEquals;

/**
 * Tests operand parameters of the main function.
 *
 * @since 2.0.0
 */

public class OperandCompilationTest {

    private static final String MAIN_FUNCTION_TEST_SRC_DIR = "test-src/main.function/operand/";

    @Test
    public void testSuccessful() {
        CompileResult result = BCompileUtil.compile(MAIN_FUNCTION_TEST_SRC_DIR + "successful.bal");
        assertEquals(result.getErrorCount(), 0);
    }

    @Test
    public void testSuccessfulArray() {
        CompileResult result = BCompileUtil.compile(MAIN_FUNCTION_TEST_SRC_DIR + "successful_array.bal");
        assertEquals(result.getErrorCount(), 0);
    }

    @Test
    public void invalidSignatureTest() {
        CompileResult negativeResult = BCompileUtil.compile(
                MAIN_FUNCTION_TEST_SRC_DIR + "invalid.bal");
        assertEquals(negativeResult.getErrorCount(), 11);
        validateError(negativeResult, 0,
                      "main function operand parameter 't' has invalid type 'typedesc'", 17, 22);
        validateError(negativeResult, 1, "main function operand parameter 'm' has invalid type '(int|string)'", 17, 39);
        validateError(negativeResult, 2, "main function operand parameter 'st1' has invalid type 'Student'", 17, 54);
        validateError(negativeResult, 3, "main function operand parameter 'n' has invalid type 'boolean'", 17, 67);
        validateError(negativeResult, 4, "main function operand parameter 'o' has invalid type 'boolean[]'", 17, 78);
        validateError(negativeResult, 5,
                      "main function operand parameter 'p' has invalid type 'boolean?'", 17, 91);
        validateError(negativeResult, 6,
                      "main function operand parameter 'q' has invalid type 'map<int>'", 17, 103);
        validateError(negativeResult, 7, "main function operand parameter 'st2' has invalid type 'Student'", 18, 5);
        validateError(negativeResult, 8, "main function operand parameter 'i' has invalid type '(int|typedesc)'", 18,
                      18);
        validateError(negativeResult, 9, "main function operand parameter 'r' has invalid type '(float|string)'", 18,
                      43);
        validateError(negativeResult, 10, "main function operand parameter 'f' has invalid type 'FooObject[]'", 18, 65);
    }

    @Test
    public void invalidSignatureWithArrayTest() {
        CompileResult negativeResult = BCompileUtil.compile(
                MAIN_FUNCTION_TEST_SRC_DIR + "invalid_array.bal");
        assertEquals(negativeResult.getErrorCount(), 3);
        validateError(negativeResult, 0, "main function operand parameter 'c' of type 'int?' cannot " +
                "follow array of type 'int'", 17, 31);
        validateError(negativeResult, 1, "main function operand parameter 'b' of type 'defaultable int' " +
                "cannot follow array of type 'int'", 17, 39);
        validateError(negativeResult, 2, "main function operand parameters can have only one array of type 'int'", 17,
                      48);
    }
}
