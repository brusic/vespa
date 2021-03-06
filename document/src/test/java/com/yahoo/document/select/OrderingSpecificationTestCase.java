// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.select.*;

/**
 * Date: Sep 6, 2007
 *
 * @author <a href="mailto:humbe@yahoo-inc.com">H&aring;kon Humberset</a>
 */
public class OrderingSpecificationTestCase extends junit.framework.TestCase {

    public OrderingSpecificationTestCase(String name) {
        super(name);
    }

    public void testExpressions() throws Exception {
        assertSelection("id.order(10,10) < 100", OrderingSpecification.DESCENDING,
                        new OrderingSpecification(OrderingSpecification.DESCENDING, (long)99, (short)10, (short)10));
        assertSelection("id.order(10,10) <= 100", OrderingSpecification.DESCENDING,
                        new OrderingSpecification(OrderingSpecification.DESCENDING, (long)100, (short)10, (short)10));
        assertSelection("id.order(10,10) > 100", OrderingSpecification.DESCENDING, null);
        assertSelection("id.order(10,10) > 100", OrderingSpecification.ASCENDING,
                        new OrderingSpecification(OrderingSpecification.ASCENDING, (long)101, (short)10, (short)10));
        assertSelection("id.user==1234 AND id.order(10,10) > 100", OrderingSpecification.ASCENDING,
                        new OrderingSpecification(OrderingSpecification.ASCENDING, (long)101, (short)10, (short)10));
        assertSelection("id.order(10,10) >= 100", OrderingSpecification.ASCENDING,
                        new OrderingSpecification(OrderingSpecification.ASCENDING, (long)100, (short)10, (short)10));
        assertSelection("id.order(10,10) == 100", OrderingSpecification.ASCENDING,
                        new OrderingSpecification(OrderingSpecification.ASCENDING, (long)100, (short)10, (short)10));
        assertSelection("id.order(10,10) = 100", OrderingSpecification.DESCENDING,
                        new OrderingSpecification(OrderingSpecification.DESCENDING, (long)100, (short)10, (short)10));
        assertSelection("id.order(10,10) > 30 AND id.order(10,10) < 100", OrderingSpecification.ASCENDING,
                        new OrderingSpecification(OrderingSpecification.ASCENDING, (long)31, (short)10, (short)10));
        assertSelection("id.order(10,10) > 30 AND id.order(10,10) < 100", OrderingSpecification.DESCENDING,
                        new OrderingSpecification(OrderingSpecification.DESCENDING, (long)99, (short)10, (short)10));
        assertSelection("id.order(10,10) > 30 OR id.order(10,10) > 70", OrderingSpecification.ASCENDING,
                        new OrderingSpecification(OrderingSpecification.ASCENDING, (long)31, (short)10, (short)10));
        assertSelection("id.order(10,10) < 30 OR id.order(10,10) < 70", OrderingSpecification.DESCENDING,
                        new OrderingSpecification(OrderingSpecification.DESCENDING, (long)69, (short)10, (short)10));
    }

    public void assertSelection(String selection, int ordering, OrderingSpecification wanted) throws Exception {
        DocumentSelector selector = new DocumentSelector(selection);
        if (wanted != null) {
            assertEquals(wanted, selector.getOrdering(ordering));
        } else {
            assertNull(selector.getOrdering(ordering));
        }
    }
}
