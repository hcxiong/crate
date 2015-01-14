/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.executor.transport;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import io.crate.Constants;
import io.crate.analyze.WhereClause;
import io.crate.executor.*;
import io.crate.executor.task.join.NestedLoopTask;
import io.crate.executor.transport.task.elasticsearch.QueryThenFetchTask;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.planner.node.dql.QueryThenFetchNode;
import io.crate.planner.node.dql.join.NestedLoopNode;
import io.crate.planner.projection.Projection;
import io.crate.planner.projection.TopNProjection;
import io.crate.planner.symbol.InputColumn;
import io.crate.planner.symbol.Symbol;
import io.crate.testing.TestingHelpers;
import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.elasticsearch.common.unit.TimeValue;
import org.junit.After;
import org.junit.Test;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;

public class TransportExecutorPagingTest extends BaseTransportExecutorTest {

    static {
        ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
    }

    private Closeable closeMeWhenDone;

    @After
    public void freeResources() throws Exception {
        if (closeMeWhenDone != null) {
            closeMeWhenDone.close();
        }
    }

    @Test
    public void testPagedQueryThenFetch() throws Exception {
        setup.setUpCharacters();
        DocTableInfo characters = docSchemaInfo.getTableInfo("characters");

        QueryThenFetchNode qtfNode = new QueryThenFetchNode(
                characters.getRouting(WhereClause.MATCH_ALL),
                Arrays.<Symbol>asList(idRef, nameRef, femaleRef),
                Arrays.<Symbol>asList(nameRef, idRef),
                new boolean[]{false, false},
                new Boolean[]{null, null},
                5,
                0,
                WhereClause.MATCH_ALL,
                null
        );

        List<Task> tasks = executor.newTasks(qtfNode, UUID.randomUUID());
        assertThat(tasks.size(), is(1));
        QueryThenFetchTask qtfTask = (QueryThenFetchTask)tasks.get(0);
        PageInfo pageInfo = PageInfo.firstPage(2);
        qtfTask.setKeepAlive(TimeValue.timeValueSeconds(10));

        qtfTask.start(pageInfo);
        List<ListenableFuture<TaskResult>> results = qtfTask.result();
        assertThat(results.size(), is(1));
        ListenableFuture<TaskResult> resultFuture = results.get(0);

        TaskResult result = resultFuture.get();
        assertThat(result, instanceOf(PageableTaskResult.class));
        closeMeWhenDone = (PageableTaskResult)result;
        assertThat(TestingHelpers.printedTable(result.rows()), is(
                "1| Arthur| false\n" +
                        "4| Arthur| true\n"
        ));
        ListenableFuture<PageableTaskResult> nextPageResultFuture = ((PageableTaskResult)result).fetch(pageInfo.nextPage(2));
        PageableTaskResult nextPageResult = nextPageResultFuture.get();
        closeMeWhenDone = nextPageResult;
        assertThat(TestingHelpers.printedTable(nextPageResult.rows()), is(
                "2| Ford| false\n" +
                        "3| Trillian| true\n"
        ));

        Object[][] nextRows = nextPageResult.fetch(pageInfo.nextPage(2)).get().rows();
        assertThat(nextRows.length, is(0));

    }

    @Test
    public void testPagedQueryThenFetchWithOffset() throws Exception {
        setup.setUpCharacters();
        DocTableInfo characters = docSchemaInfo.getTableInfo("characters");

        QueryThenFetchNode qtfNode = new QueryThenFetchNode(
                characters.getRouting(WhereClause.MATCH_ALL),
                Arrays.<Symbol>asList(idRef, nameRef, femaleRef),
                Arrays.<Symbol>asList(nameRef, idRef),
                new boolean[]{false, false},
                new Boolean[]{null, null},
                5,
                0,
                WhereClause.MATCH_ALL,
                null
        );

        List<Task> tasks = executor.newTasks(qtfNode, UUID.randomUUID());
        assertThat(tasks.size(), is(1));
        QueryThenFetchTask qtfTask = (QueryThenFetchTask)tasks.get(0);
        PageInfo pageInfo = new PageInfo(1, 2);
        qtfTask.setKeepAlive(TimeValue.timeValueSeconds(10));
        qtfTask.start(pageInfo);
        List<ListenableFuture<TaskResult>> results = qtfTask.result();
        assertThat(results.size(), is(1));
        ListenableFuture<TaskResult> resultFuture = results.get(0);
        TaskResult result = resultFuture.get();
        assertThat(result, instanceOf(PageableTaskResult.class));
        closeMeWhenDone = (PageableTaskResult)result;
        assertThat(TestingHelpers.printedTable(result.rows()), is(
                "4| Arthur| true\n" +
                        "2| Ford| false\n"
        ));
        ListenableFuture<PageableTaskResult> nextPageResultFuture = ((PageableTaskResult)result).fetch(pageInfo.nextPage(2));
        PageableTaskResult nextPageResult = nextPageResultFuture.get();
        closeMeWhenDone = nextPageResult;
        assertThat(TestingHelpers.printedTable(nextPageResult.rows()), is(
                "3| Trillian| true\n"
        ));

        Object[][] nextRows = nextPageResult.fetch(pageInfo.nextPage(2)).get().rows();
        assertThat(nextRows.length, is(0));
    }

    @Test
    public void testPagedQueryThenFetchWithoutSorting() throws Exception {
        setup.setUpCharacters();
        DocTableInfo characters = docSchemaInfo.getTableInfo("characters");

        QueryThenFetchNode qtfNode = new QueryThenFetchNode(
                characters.getRouting(WhereClause.MATCH_ALL),
                Arrays.<Symbol>asList(idRef, nameRef, femaleRef),
                null,
                null,
                null,
                5,
                0,
                WhereClause.MATCH_ALL,
                null
        );

        List<Task> tasks = executor.newTasks(qtfNode, UUID.randomUUID());
        assertThat(tasks.size(), is(1));
        QueryThenFetchTask qtfTask = (QueryThenFetchTask)tasks.get(0);
        PageInfo pageInfo = new PageInfo(1, 2);
        qtfTask.setKeepAlive(TimeValue.timeValueSeconds(10));
        qtfTask.start(pageInfo);
        List<ListenableFuture<TaskResult>> results = qtfTask.result();
        assertThat(results.size(), is(1));

        ListenableFuture<TaskResult> resultFuture = results.get(0);
        TaskResult result = resultFuture.get();
        assertThat(result, instanceOf(PageableTaskResult.class));
        closeMeWhenDone = (PageableTaskResult)result;

        assertThat(result.rows().length, is(2));

        ListenableFuture<PageableTaskResult> nextPageResultFuture = ((PageableTaskResult)result).fetch(pageInfo.nextPage(2));
        PageableTaskResult nextPageResult = nextPageResultFuture.get();
        closeMeWhenDone = nextPageResult;
        assertThat(nextPageResult.rows().length, is(1));

        PageableTaskResult furtherPageResult = nextPageResult.fetch(pageInfo.nextPage(2)).get();
        closeMeWhenDone = furtherPageResult;
        assertThat(furtherPageResult.rows().length, is(0));
    }

    @Test
    public void testNestedLoopBothSidesPageableNoLimit() throws Exception {
        setup.setUpCharacters();
        setup.setUpBooks();

        DocTableInfo characters = docSchemaInfo.getTableInfo("characters");

        QueryThenFetchNode leftNode = new QueryThenFetchNode(
                characters.getRouting(WhereClause.MATCH_ALL),
                Arrays.<Symbol>asList(idRef, nameRef, femaleRef),
                Arrays.<Symbol>asList(nameRef, femaleRef),
                new boolean[]{false, true},
                new Boolean[]{null, null},
                5,
                0,
                WhereClause.MATCH_ALL,
                null
        );
        leftNode.outputTypes(ImmutableList.of(
                        idRef.info().type(),
                        nameRef.info().type(),
                        femaleRef.info().type())
        );

        DocTableInfo books = docSchemaInfo.getTableInfo("books");
        QueryThenFetchNode rightNode = new QueryThenFetchNode(
                books.getRouting(WhereClause.MATCH_ALL),
                Arrays.<Symbol>asList(titleRef),
                Arrays.<Symbol>asList(titleRef),
                new boolean[]{false},
                new Boolean[]{null},
                null,
                null,
                WhereClause.MATCH_ALL,
                null
        );
        rightNode.outputTypes(ImmutableList.of(
                        authorRef.info().type())
        );

        TopNProjection projection = new TopNProjection(Constants.DEFAULT_SELECT_LIMIT, 0);
        projection.outputs(ImmutableList.<Symbol>of(
                new InputColumn(0, DataTypes.INTEGER),
                new InputColumn(1, DataTypes.STRING),
                new InputColumn(2, DataTypes.BOOLEAN),
                new InputColumn(3, DataTypes.STRING)
        ));
        List<DataType> outputTypes = ImmutableList.of(
                idRef.info().type(),
                nameRef.info().type(),
                femaleRef.info().type(),
                titleRef.info().type());


        // SELECT characters.id, characters.name, characters.female, books.title
        // FROM characters CROSS JOIN books
        // ORDER BY character.name, character.female, books.title
        NestedLoopNode node = new NestedLoopNode(leftNode, rightNode, true, Constants.DEFAULT_SELECT_LIMIT, 0);
        node.projections(ImmutableList.<Projection>of(projection));
        node.outputTypes(outputTypes);

        List<Task> tasks = executor.newTasks(node, UUID.randomUUID());
        assertThat(tasks.size(), is(1));
        assertThat(tasks.get(0), instanceOf(NestedLoopTask.class));

        NestedLoopTask nestedLoopTask = (NestedLoopTask) tasks.get(0);

        List<ListenableFuture<TaskResult>> results = nestedLoopTask.result();
        assertThat(results.size(), is(1));
        nestedLoopTask.start();
        TaskResult result = results.get(0).get();
        assertThat(result, instanceOf(QueryResult.class));
        assertThat(TestingHelpers.printedTable(result.rows()), is(
                "4| Arthur| true| Life, the Universe and Everything\n" +
                        "4| Arthur| true| The Hitchhiker's Guide to the Galaxy\n" +
                        "4| Arthur| true| The Restaurant at the End of the Universe\n" +
                        "1| Arthur| false| Life, the Universe and Everything\n" +
                        "1| Arthur| false| The Hitchhiker's Guide to the Galaxy\n" +
                        "1| Arthur| false| The Restaurant at the End of the Universe\n" +
                        "2| Ford| false| Life, the Universe and Everything\n" +
                        "2| Ford| false| The Hitchhiker's Guide to the Galaxy\n" +
                        "2| Ford| false| The Restaurant at the End of the Universe\n" +
                        "3| Trillian| true| Life, the Universe and Everything\n" +
                        "3| Trillian| true| The Hitchhiker's Guide to the Galaxy\n" +
                        "3| Trillian| true| The Restaurant at the End of the Universe\n"));
    }

    @Test
    public void testNestedLoopBothSidesPageableLimitAndOffset() throws Exception {
        setup.setUpCharacters();
        setup.setUpBooks();

        DocTableInfo characters = docSchemaInfo.getTableInfo("characters");

        QueryThenFetchNode leftNode = new QueryThenFetchNode(
                characters.getRouting(WhereClause.MATCH_ALL),
                Arrays.<Symbol>asList(idRef, nameRef, femaleRef),
                Arrays.<Symbol>asList(nameRef, femaleRef),
                new boolean[]{false, true},
                new Boolean[]{null, null},
                5,
                0,
                WhereClause.MATCH_ALL,
                null
        );
        leftNode.outputTypes(ImmutableList.of(
                        idRef.info().type(),
                        nameRef.info().type(),
                        femaleRef.info().type())
        );

        DocTableInfo books = docSchemaInfo.getTableInfo("books");
        QueryThenFetchNode rightNode = new QueryThenFetchNode(
                books.getRouting(WhereClause.MATCH_ALL),
                Arrays.<Symbol>asList(titleRef),
                Arrays.<Symbol>asList(titleRef),
                new boolean[]{false},
                new Boolean[]{null},
                null,
                null,
                WhereClause.MATCH_ALL,
                null
        );
        rightNode.outputTypes(ImmutableList.of(
                        authorRef.info().type())
        );

        TopNProjection projection = new TopNProjection(10, 1);
        projection.outputs(ImmutableList.<Symbol>of(
                new InputColumn(0, DataTypes.INTEGER),
                new InputColumn(1, DataTypes.STRING),
                new InputColumn(2, DataTypes.BOOLEAN),
                new InputColumn(3, DataTypes.STRING)
        ));
        List<DataType> outputTypes = ImmutableList.of(
                idRef.info().type(),
                nameRef.info().type(),
                femaleRef.info().type(),
                titleRef.info().type());


        // SELECT characters.id, characters.name, characters.female, books.title
        // FROM characters CROSS JOIN books
        // ORDER BY character.name, character.female, books.title
        NestedLoopNode node = new NestedLoopNode(leftNode, rightNode, true, 10, 1);
        node.projections(ImmutableList.<Projection>of(projection));
        node.outputTypes(outputTypes);

        List<Task> tasks = executor.newTasks(node, UUID.randomUUID());
        assertThat(tasks.size(), is(1));
        assertThat(tasks.get(0), instanceOf(NestedLoopTask.class));

        NestedLoopTask nestedLoopTask = (NestedLoopTask) tasks.get(0);

        List<ListenableFuture<TaskResult>> results = nestedLoopTask.result();
        assertThat(results.size(), is(1));
        nestedLoopTask.start();
        TaskResult result = results.get(0).get();
        assertThat(result, instanceOf(QueryResult.class));
        assertThat(TestingHelpers.printedTable(result.rows()), is(
                "4| Arthur| true| The Hitchhiker's Guide to the Galaxy\n" +
                        "4| Arthur| true| The Restaurant at the End of the Universe\n" +
                        "1| Arthur| false| Life, the Universe and Everything\n" +
                        "1| Arthur| false| The Hitchhiker's Guide to the Galaxy\n" +
                        "1| Arthur| false| The Restaurant at the End of the Universe\n" +
                        "2| Ford| false| Life, the Universe and Everything\n" +
                        "2| Ford| false| The Hitchhiker's Guide to the Galaxy\n" +
                        "2| Ford| false| The Restaurant at the End of the Universe\n" +
                        "3| Trillian| true| Life, the Universe and Everything\n" +
                        "3| Trillian| true| The Hitchhiker's Guide to the Galaxy\n"));
    }
}
