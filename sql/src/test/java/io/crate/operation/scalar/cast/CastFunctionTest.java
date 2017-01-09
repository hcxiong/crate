/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.operation.scalar.cast;

import io.crate.operation.scalar.AbstractScalarFunctionsTest;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;

import static io.crate.testing.SymbolMatchers.isFunction;


public class CastFunctionTest extends AbstractScalarFunctionsTest {

    // cast is just a wrapper around  DataType.value(val) which is why here are just a few tests

    @Test
    public void testNormalize() throws Exception {
        assertNormalize("cast(name as long)", isFunction("to_long"));
    }

    @Test
    public void testCasts() throws Exception {
        assertEvaluate("cast(10.4 as string)", new BytesRef("10.4"));
        assertEvaluate("cast(null as string)", null);
        assertEvaluate("cast(10.4 as long)", 10L);
        assertEvaluate("to_long_array([10.2, 12.3])", new Long[] { 10L, 12L });
    }

    @Test
    public void testDoubleColonOperatorCast() {
        assertEvaluate("10.4::string", new BytesRef("10.4"));
        assertEvaluate("[1, 2, 0]::array(boolean)", new Boolean[]{true, true, false});
        assertEvaluate("(1+3)/2::string", new BytesRef("2"));
        assertEvaluate("'10'::long + 5", 15L);
        assertEvaluate("-4::string", new BytesRef("-4"));
        assertEvaluate("'-4'::long", -4L);
        assertEvaluate("-4::string || ' apples'", new BytesRef("-4 apples"));
        assertEvaluate("'-4'::long + 10", 6L);
    }
}
