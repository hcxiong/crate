/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.analyze;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import io.crate.analyze.expressions.ExpressionAnalysisContext;
import io.crate.analyze.expressions.ExpressionAnalyzer;
import io.crate.analyze.relations.*;
import io.crate.analyze.symbol.Symbol;
import io.crate.analyze.symbol.Symbols;
import io.crate.analyze.where.WhereClauseAnalyzer;
import io.crate.exceptions.UnsupportedFeatureException;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.doc.DocSysColumns;
import io.crate.metadata.table.Operation;
import io.crate.sql.tree.Delete;
import io.crate.sql.tree.Expression;
import io.crate.sql.tree.ParameterExpression;

class DeleteAnalyzer {

    private static final String VERSION_SEARCH_EX_MSG =
        "_version is not allowed in delete queries without specifying a primary key";
    private static final UnsupportedFeatureException VERSION_SEARCH_EX = new UnsupportedFeatureException(
        VERSION_SEARCH_EX_MSG);

    private AnalysisMetaData analysisMetaData;
    private RelationAnalyzer relationAnalyzer;

    DeleteAnalyzer(AnalysisMetaData analysisMetaData, RelationAnalyzer relationAnalyzer) {
        this.analysisMetaData = analysisMetaData;
        this.relationAnalyzer = relationAnalyzer;
    }

    public AnalyzedStatement analyze(Delete node, Analysis analysis) {
        int numNested = 1;

        Function<ParameterExpression, Symbol> convertParamFunction = analysis.parameterContext();
        StatementAnalysisContext statementAnalysisContext = new StatementAnalysisContext(
            analysis.sessionContext(),
            convertParamFunction,
            analysis.transactionContext(),
            analysisMetaData,
            Operation.DELETE);
        RelationAnalysisContext relationAnalysisContext = statementAnalysisContext.startRelation();
        AnalyzedRelation analyzedRelation = relationAnalyzer.analyze(node.getRelation(), statementAnalysisContext);

        assert analyzedRelation instanceof DocTableRelation;
        DocTableRelation docTableRelation = (DocTableRelation) analyzedRelation;
        DeleteAnalyzedStatement deleteAnalyzedStatement = new DeleteAnalyzedStatement(docTableRelation);
        ExpressionAnalyzer expressionAnalyzer = new ExpressionAnalyzer(
            analysisMetaData,
            analysis.sessionContext(),
            convertParamFunction,
            new FullQualifedNameFieldProvider(relationAnalysisContext.sources()),
            docTableRelation);
        ExpressionAnalysisContext expressionAnalysisContext = new ExpressionAnalysisContext();
        WhereClauseAnalyzer whereClauseAnalyzer = new WhereClauseAnalyzer(analysisMetaData, deleteAnalyzedStatement.analyzedRelation());

        if (analysis.parameterContext().hasBulkParams()) {
            numNested = analysis.parameterContext().numBulkParams();
        }
        for (int i = 0; i < numNested; i++) {
            analysis.parameterContext().setBulkIdx(i);
            deleteAnalyzedStatement.whereClauses.add(generateWhereClause(
                node.getWhere(),
                whereClauseAnalyzer,
                expressionAnalyzer,
                expressionAnalysisContext,
                analysis.transactionContext()));
        }

        statementAnalysisContext.endRelation();
        return deleteAnalyzedStatement;
    }

    private WhereClause generateWhereClause(Optional<Expression> where,
                                            WhereClauseAnalyzer whereClauseAnalyzer,
                                            ExpressionAnalyzer expressionAnalyzer,
                                            ExpressionAnalysisContext expressionAnalysisContext,
                                            TransactionContext transactionContext) {
        WhereClause whereClause = whereClauseAnalyzer.analyze(
            expressionAnalyzer.generateWhereClause(where, expressionAnalysisContext, transactionContext), transactionContext);
        if (!whereClause.docKeys().isPresent() && Symbols.containsColumn(whereClause.query(), DocSysColumns.VERSION)) {
            throw VERSION_SEARCH_EX;
        }
        return whereClause;
    }
}