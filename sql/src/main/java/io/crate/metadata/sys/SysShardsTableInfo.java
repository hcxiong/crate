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

package io.crate.metadata.sys;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.crate.action.sql.SessionContext;
import io.crate.analyze.WhereClause;
import io.crate.analyze.user.Privilege;
import io.crate.auth.user.User;
import io.crate.expression.NestableInput;
import io.crate.metadata.ColumnIdent;
import io.crate.metadata.ReferenceIdent;
import io.crate.metadata.RelationName;
import io.crate.metadata.Routing;
import io.crate.metadata.RoutingProvider;
import io.crate.metadata.RowContextCollectorExpression;
import io.crate.metadata.RowGranularity;
import io.crate.metadata.expressions.RowCollectExpressionFactory;
import io.crate.metadata.shard.unassigned.UnassignedShard;
import io.crate.metadata.table.ColumnRegistrar;
import io.crate.metadata.table.StaticTableInfo;
import io.crate.types.BooleanType;
import io.crate.types.DataTypes;
import io.crate.types.FloatType;
import io.crate.types.IntegerType;
import io.crate.types.LongType;
import io.crate.types.ObjectType;
import io.crate.types.StringType;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.routing.GroupShardsIterator;
import org.elasticsearch.cluster.routing.ShardIterator;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.index.shard.ShardId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SysShardsTableInfo extends StaticTableInfo {

    public static final RelationName IDENT = new RelationName(SysSchemaInfo.NAME, "shards");

    public static class Columns {
        public static final ColumnIdent ID = new ColumnIdent("id");
        static final ColumnIdent SCHEMA_NAME = new ColumnIdent("schema_name");
        public static final ColumnIdent TABLE_NAME = new ColumnIdent("table_name");
        static final ColumnIdent PARTITION_IDENT = new ColumnIdent("partition_ident");
        static final ColumnIdent NUM_DOCS = new ColumnIdent("num_docs");
        public static final ColumnIdent PRIMARY = new ColumnIdent("primary");
        static final ColumnIdent RELOCATING_NODE = new ColumnIdent("relocating_node");
        public static final ColumnIdent SIZE = new ColumnIdent("size");
        static final ColumnIdent STATE = new ColumnIdent("state");
        static final ColumnIdent ROUTING_STATE = new ColumnIdent("routing_state");
        static final ColumnIdent ORPHAN_PARTITION = new ColumnIdent("orphan_partition");

        static final ColumnIdent RECOVERY = new ColumnIdent("recovery");
        static final ColumnIdent RECOVERY_STAGE = new ColumnIdent("recovery", ImmutableList.of("stage"));
        static final ColumnIdent RECOVERY_TYPE = new ColumnIdent("recovery", ImmutableList.of("type"));
        static final ColumnIdent RECOVERY_TOTAL_TIME =
            new ColumnIdent("recovery", ImmutableList.of("total_time"));

        static final ColumnIdent RECOVERY_FILES = new ColumnIdent("recovery", ImmutableList.of("files"));
        static final ColumnIdent RECOVERY_FILES_USED =
            new ColumnIdent("recovery", ImmutableList.of("files", "used"));
        static final ColumnIdent RECOVERY_FILES_REUSED =
            new ColumnIdent("recovery", ImmutableList.of("files", "reused"));
        static final ColumnIdent RECOVERY_FILES_RECOVERED =
            new ColumnIdent("recovery", ImmutableList.of("files", "recovered"));
        static final ColumnIdent RECOVERY_FILES_PERCENT =
            new ColumnIdent("recovery", ImmutableList.of("files", "percent"));

        static final ColumnIdent RECOVERY_SIZE =
            new ColumnIdent("recovery", ImmutableList.of("size"));
        static final ColumnIdent RECOVERY_SIZE_USED =
            new ColumnIdent("recovery", ImmutableList.of("size", "used"));
        static final ColumnIdent RECOVERY_SIZE_REUSED =
            new ColumnIdent("recovery", ImmutableList.of("size", "reused"));
        static final ColumnIdent RECOVERY_SIZE_RECOVERED =
            new ColumnIdent("recovery", ImmutableList.of("size", "recovered"));
        static final ColumnIdent RECOVERY_SIZE_PERCENT =
            new ColumnIdent("recovery", ImmutableList.of("size", "percent"));

        static final ColumnIdent PATH = new ColumnIdent("path");
        static final ColumnIdent BLOB_PATH = new ColumnIdent("blob_path");

        static final ColumnIdent MIN_LUCENE_VERSION = new ColumnIdent("min_lucene_version");
        static final ColumnIdent NODE = new ColumnIdent("node");
        static final ColumnIdent NODE_ID = new ColumnIdent("node", "id");
        static final ColumnIdent NODE_NAME = new ColumnIdent("node", "name");
    }

    public static class ReferenceIdents {

        /**
         * Implementations have to be registered in
         *  - {@link io.crate.metadata.shard.ShardReferenceResolver}
         *  - {@link io.crate.metadata.shard.blob.BlobShardReferenceResolver}
         *  - {@link #unassignedShardsExpressions()}
         */

        public static final ReferenceIdent ID = new ReferenceIdent(IDENT, Columns.ID);
        public static final ReferenceIdent SCHEMA_NAME = new ReferenceIdent(IDENT, Columns.SCHEMA_NAME);
        public static final ReferenceIdent TABLE_NAME = new ReferenceIdent(IDENT, Columns.TABLE_NAME);
        public static final ReferenceIdent PARTITION_IDENT = new ReferenceIdent(IDENT, Columns.PARTITION_IDENT);
        public static final ReferenceIdent NUM_DOCS = new ReferenceIdent(IDENT, Columns.NUM_DOCS);
        public static final ReferenceIdent PRIMARY = new ReferenceIdent(IDENT, Columns.PRIMARY);
        public static final ReferenceIdent RELOCATING_NODE = new ReferenceIdent(IDENT, Columns.RELOCATING_NODE);
        public static final ReferenceIdent SIZE = new ReferenceIdent(IDENT, Columns.SIZE);
        public static final ReferenceIdent STATE = new ReferenceIdent(IDENT, Columns.STATE);
        public static final ReferenceIdent ROUTING_STATE = new ReferenceIdent(IDENT, Columns.ROUTING_STATE);
        public static final ReferenceIdent ORPHAN_PARTITION = new ReferenceIdent(IDENT, Columns.ORPHAN_PARTITION);
        public static final ReferenceIdent RECOVERY = new ReferenceIdent(IDENT, Columns.RECOVERY);
        public static final ReferenceIdent PATH = new ReferenceIdent(IDENT, Columns.PATH);
        public static final ReferenceIdent BLOB_PATH = new ReferenceIdent(IDENT, Columns.BLOB_PATH);
        public static final ReferenceIdent MIN_LUCENE_VERSION = new ReferenceIdent(IDENT, Columns.MIN_LUCENE_VERSION);
        public static final ReferenceIdent NODE = new ReferenceIdent(IDENT, Columns.NODE);
    }

    public static Map<ColumnIdent, RowCollectExpressionFactory<UnassignedShard>> unassignedShardsExpressions() {
        return ImmutableMap.<ColumnIdent, RowCollectExpressionFactory<UnassignedShard>>builder()
            .put(Columns.SCHEMA_NAME,
                () -> RowContextCollectorExpression.objToBytesRef(UnassignedShard::schemaName))
            .put(Columns.TABLE_NAME,
                () -> RowContextCollectorExpression.objToBytesRef(UnassignedShard::tableName))
            .put(Columns.PARTITION_IDENT,
                () -> RowContextCollectorExpression.objToBytesRef(UnassignedShard::partitionIdent))
            .put(Columns.ID,
                () -> RowContextCollectorExpression.forFunction(UnassignedShard::id))
            .put(Columns.NUM_DOCS,
                () -> RowContextCollectorExpression.forFunction(r -> 0L))
            .put(Columns.PRIMARY,
                () -> RowContextCollectorExpression.forFunction(UnassignedShard::primary))
            .put(Columns.RELOCATING_NODE,
                () -> RowContextCollectorExpression.objToBytesRef(r -> null))
            .put(Columns.SIZE,
                () -> RowContextCollectorExpression.forFunction(r -> 0L))
            .put(Columns.STATE,
                () -> RowContextCollectorExpression.objToBytesRef(UnassignedShard::state))
            .put(Columns.ROUTING_STATE,
                () -> RowContextCollectorExpression.objToBytesRef(UnassignedShard::state))
            .put(Columns.ORPHAN_PARTITION,
                () -> RowContextCollectorExpression.forFunction(UnassignedShard::orphanedPartition))
            .put(Columns.RECOVERY, () -> new RowContextCollectorExpression<UnassignedShard, Object>() {
                @Override
                public Object value() {
                    return null;
                }

                @Override
                public NestableInput getChild(String name) {
                    return this;
                }
            })
            .put(Columns.PATH,
                () -> RowContextCollectorExpression.objToBytesRef(r -> null))
            .put(Columns.BLOB_PATH,
                () -> RowContextCollectorExpression.objToBytesRef(r -> null))
            .put(Columns.MIN_LUCENE_VERSION,
                () -> RowContextCollectorExpression.objToBytesRef(r -> null))
            .put(Columns.NODE, () -> new RowContextCollectorExpression<UnassignedShard, Object>() {
                @Override
                public Object value() {
                    return null; // unassigned shards are on *no* node.
                }

                @Override
                public NestableInput<?> getChild(String name) {
                    return this;
                }
            })
            .build();
    }

    private static final ImmutableList<ColumnIdent> PRIMARY_KEY = ImmutableList.of(
        Columns.SCHEMA_NAME,
        Columns.TABLE_NAME,
        Columns.ID,
        Columns.PARTITION_IDENT
    );

    SysShardsTableInfo() {
        super(IDENT, new ColumnRegistrar(IDENT, RowGranularity.SHARD)
                .register(Columns.SCHEMA_NAME, StringType.INSTANCE)
                .register(Columns.TABLE_NAME, StringType.INSTANCE)
                .register(Columns.ID, IntegerType.INSTANCE)
                .register(Columns.PARTITION_IDENT, StringType.INSTANCE)
                .register(Columns.NUM_DOCS, LongType.INSTANCE)
                .register(Columns.PRIMARY, BooleanType.INSTANCE)
                .register(Columns.RELOCATING_NODE, StringType.INSTANCE)
                .register(Columns.SIZE, LongType.INSTANCE)
                .register(Columns.STATE, StringType.INSTANCE)
                .register(Columns.ROUTING_STATE, StringType.INSTANCE)
                .register(Columns.ORPHAN_PARTITION, BooleanType.INSTANCE)

                .register(Columns.RECOVERY, ObjectType.INSTANCE)
                .register(Columns.RECOVERY_STAGE, StringType.INSTANCE)
                .register(Columns.RECOVERY_TYPE, StringType.INSTANCE)
                .register(Columns.RECOVERY_TOTAL_TIME, LongType.INSTANCE)

                .register(Columns.RECOVERY_SIZE, ObjectType.INSTANCE)
                .register(Columns.RECOVERY_SIZE_USED, LongType.INSTANCE)
                .register(Columns.RECOVERY_SIZE_REUSED, LongType.INSTANCE)
                .register(Columns.RECOVERY_SIZE_RECOVERED, LongType.INSTANCE)
                .register(Columns.RECOVERY_SIZE_PERCENT, FloatType.INSTANCE)

                .register(Columns.RECOVERY_FILES, ObjectType.INSTANCE)
                .register(Columns.RECOVERY_FILES_USED, IntegerType.INSTANCE)
                .register(Columns.RECOVERY_FILES_REUSED, IntegerType.INSTANCE)
                .register(Columns.RECOVERY_FILES_RECOVERED, IntegerType.INSTANCE)
                .register(Columns.RECOVERY_FILES_PERCENT, FloatType.INSTANCE)
                .register(Columns.PATH, DataTypes.STRING)
                .register(Columns.BLOB_PATH, DataTypes.STRING)

                .register(Columns.MIN_LUCENE_VERSION, StringType.INSTANCE)
                .register(Columns.NODE, DataTypes.OBJECT)
                .register(Columns.NODE_ID, DataTypes.STRING)
                .register(Columns.NODE_NAME, DataTypes.STRING),
            PRIMARY_KEY);
    }

    private void processShardRouting(String localNodeId,
                                     Map<String, Map<String, List<Integer>>> routing,
                                     ShardRouting shardRouting,
                                     ShardId shardId) {
        String node;
        int id;
        String index = shardId.getIndex().getName();

        if (shardRouting == null) {
            node = localNodeId;
            id = UnassignedShard.markUnassigned(shardId.id());
        } else {
            node = shardRouting.currentNodeId();
            id = shardRouting.id();
        }
        Map<String, List<Integer>> nodeMap = routing.get(node);
        if (nodeMap == null) {
            nodeMap = new TreeMap<>();
            routing.put(node, nodeMap);
        }

        List<Integer> shards = nodeMap.get(index);
        if (shards == null) {
            shards = new ArrayList<>();
            nodeMap.put(index, shards);
        }
        shards.add(id);
    }

    @Override
    public RowGranularity rowGranularity() {
        return RowGranularity.SHARD;
    }

    /**
     * Retrieves the routing for sys.shards
     * <p>
     * This routing contains ALL shards of ALL indices.
     * Any shards that are not yet assigned to a node will have a NEGATIVE shard id (see {@link UnassignedShard}
     */
    @Override
    public Routing getRouting(ClusterState clusterState,
                              RoutingProvider routingProvider,
                              WhereClause whereClause,
                              RoutingProvider.ShardSelection shardSelection,
                              SessionContext sessionContext) {
        // TODO: filter on whereClause
        Map<String, Map<String, List<Integer>>> locations = new TreeMap<>();
        String[] concreteIndices = clusterState.metaData().getConcreteAllOpenIndices();

        User user = sessionContext != null ? sessionContext.user() : null;
        if (user != null) {
            List<String> accessibleTables = new ArrayList<>(concreteIndices.length);
            for (String indexName : concreteIndices) {
                String tableName = RelationName.fqnFromIndexName(indexName);
                if (user.hasAnyPrivilege(Privilege.Clazz.TABLE, tableName)) {
                    accessibleTables.add(indexName);
                }
            }
            concreteIndices = accessibleTables.toArray(new String[0]);
        }

        GroupShardsIterator<ShardIterator> groupShardsIterator =
            clusterState.getRoutingTable().allAssignedShardsGrouped(concreteIndices, true, true);
        for (final ShardIterator shardIt : groupShardsIterator) {
            final ShardRouting shardRouting = shardIt.nextOrNull();
            processShardRouting(clusterState.getNodes().getLocalNodeId(), locations, shardRouting, shardIt.shardId());
        }
        return new Routing(locations);
    }
}
