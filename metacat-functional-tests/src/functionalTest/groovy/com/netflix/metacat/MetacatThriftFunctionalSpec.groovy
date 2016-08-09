/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package com.netflix.metacat

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.netflix.metacat.common.QualifiedName
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.metastore.TableType
import org.apache.hadoop.hive.metastore.Warehouse
import org.apache.hadoop.hive.metastore.api.Database
import org.apache.hadoop.hive.metastore.api.FieldSchema
import org.apache.hadoop.hive.metastore.api.InvalidOperationException
import org.apache.hadoop.hive.metastore.api.SerDeInfo
import org.apache.hadoop.hive.metastore.api.StorageDescriptor
import org.apache.hadoop.hive.ql.metadata.Hive
import org.apache.hadoop.hive.ql.metadata.HiveException
import org.apache.hadoop.hive.ql.metadata.InvalidTableException
import org.apache.hadoop.hive.ql.metadata.Partition
import org.apache.hadoop.hive.ql.metadata.Table
import org.apache.hadoop.hive.ql.plan.AddPartitionDesc
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

@Stepwise
@Unroll
class MetacatThriftFunctionalSpec extends Specification {
    public static final long BATCH_ID = System.currentTimeSeconds()

    // METHODS TO TEST
//    alterFunction
//    alterIndex
//    alterIndex
//    alterPartition
//    alterPartition
//    alterPartitions
//    alterTable
//    alterTable
//    cancelDelegationToken
//    clearMetaCallTiming
//    closeCurrent
//    compact
//    createFunction
//    createIndex
//    createRole
//    deletePartitionColumnStatistics
//    deleteTableColumnStatistics
//    dropFunction
//    dropIndex
//    dropIndex
//    dropPartitions
//    dropPartitions
//    dropPartitions
//    dropPartitions
//    dropRole
//    dumpAndClearMetaCallTiming
//    exchangeTablePartitions
//    get
//    get
//    get
//    get
//    get_privilege_set
//    getAggrColStatsFor
//    getAllPartitionsOf
//    getAllRoleNames
//    getDelegationToken
//    getFieldsFromDeserializer
//    getFunction
//    getFunctions
//    getIndex
//    getIndex
//    getIndexes
//    getMetaConf
//    getMSC
//    getPartitionColumnStatistics
//    getPartitionsByExpr
//    getRoleGrantInfoForPrincipal
//    getTableColumnStatistics
//    getTablesByPattern
//    getTablesByPattern
//    getTablesForDb
//    grantPrivileges
//    grantRole
//    isHadoop1
//    listRoles
//    loadDynamicPartitions
//    loadPartition
//    loadPartition
//    loadTable
//    moveFile
//    newTable
//    reloadFunctions
//    renamePartition
//    revokePrivileges
//    revokeRole
//    set
//    setMetaConf
//    setPartitionColumnStatistics
//    showCompactions
//    showPrivilegeGrant
//    showTransactions
//    updatePartitionColumnStatistics
//    updateTableColumnStatistics
//    validatePartitionNameCharacters

    @Shared
    String hiveThriftUri
    @Shared
    String metacatHiveThriftUri
    @Shared
    String metacatS3ThriftUri

    LoadingCache<String, Hive> METASTORES = CacheBuilder.newBuilder()
            .build(
            new CacheLoader<String, Hive>() {
                public Hive load(String thriftUri) throws Exception {
                    assert thriftUri, 'Thrift uri is required'

                    HiveConf conf = new HiveConf()
                    conf.set('hive.metastore.uris', thriftUri)
                    conf.set('hive.metastore.sasl.enabled', 'false')
                    conf.set('hive.allow-drop-table', 'true')
                    conf.set('hive.allow-rename-table', 'true')
                    conf.set('hive.assume-canonical-partition-keys', 'true')
                    conf.set('hive.metastore-cache-ttl', '1m')
                    conf.set('hive.metastore-refresh-interval', '1m')
                    conf.set('hive.metastore-timeout', '20s')
                    conf.set('hive.parquet.use-column-names', 'false')
                    conf.set('hive.s3.connect-timeout', '2m')
                    conf.set('hive.s3.max-backoff-time', '10m')
                    conf.set('hive.s3.max-client-retries', '25')
                    conf.set('hive.s3.max-connections', '500')
                    conf.set('hive.s3.max-error-retries', '50')
                    conf.set('hive.s3.max-retry-time', '10m')
                    conf.set('hive.s3.socket-timeout', '2m')
                    conf.set('hive.s3.staging-directory', '/tmp')
                    conf.set('hive.storage-format', 'PARQUET')

                    // Hive.get stores the client in a thread local, use the private constructor to manage our own cache
                    return new Hive(conf)
                }
            });

    def setupSpec() {
        String dockerIp = System.properties['metacat_docker_ip']?.toString()?.trim()
        assert dockerIp, 'Required system property "metacat_docker_ip" is not set'
        String thriftPort = System.properties['metacat_hive_thrift_port']?.toString()?.trim()
        assert thriftPort, 'Required system property "metacat_hive_thrift_port" is not set'
        metacatHiveThriftUri = "thrift://${dockerIp}:${thriftPort}".toString()
        TestCatalogs.findByCatalogName('hive-metastore').thriftUri = metacatHiveThriftUri

        // TODO enable this, commenting out because s3 type currently doesn't have enough places to store parameters
        // currently only table.location.info.parameters and we need 2 more parameters
//        thriftPort = System.properties['metacat_s3_thrift_port']?.toString()?.trim()
//        assert thriftPort, 'Required system property "metacat_s3_thrift_port" is not set'
//        metacatS3ThriftUri = "thrift://${dockerIp}:${thriftPort}".toString()
//        TestCatalogs.findByCatalogName('s3-mysql-db').thriftUri = metacatS3ThriftUri

        thriftPort = System.properties['hive_thrift_port']?.toString()?.trim()
        assert thriftPort, 'Required system property "hive_thrift_port" is not set'
        hiveThriftUri = "thrift://${dockerIp}:${thriftPort}".toString()

        TestCatalogs.resetAll()
    }

    List<String> databaseDifferences(Database actual, Database expected) {
        if (actual == expected) {
            return []
        }

        List<String> result = []
        if (actual.name != expected.name) {
            result << "database.name: $actual.name != $expected.name"
        }

        if (actual.locationUri != expected.locationUri) {
            result << "database.locationUri: $actual.locationUri != $expected.locationUri"
        }

        if (actual.parameters != expected.parameters) {
            result << "database.parameters: $actual.parameters != $expected.parameters"
        }

        return result
    }

    List<String> fieldSchemaDifferences(String prefix, FieldSchema actual, FieldSchema expected) {
        if (actual == expected) {
            return []
        }

        List<String> result = []
        if (actual.name != expected.name) {
            result << "${prefix}.name:  $actual.name != $expected.name"
        }

        if (actual.type != expected.type) {
            result << "${prefix}.type:  $actual.type != $expected.type"
        }

        if (actual.comment != expected.comment) {
            result << "${prefix}.comment:  $actual.comment != $expected.comment"
        }

        return result
    }

    List<String> storageDescriptorDifferences(String prefix, StorageDescriptor actual, StorageDescriptor expected) {
        if (actual == expected) {
            return []
        }

        List<String> result = []
        for (int i; i < Math.max(actual.cols.size(), expected.cols.size()); i++) {
            def fActual = actual.cols.size() >= i ? actual.cols[i] : null
            def fExpected = expected.cols.size() >= i ? expected.cols[i] : null
            if (!fActual || !fExpected) {
                result << "${prefix}.cols[$i]: $fActual != $fExpected"
            } else {
                result.addAll(fieldSchemaDifferences("${prefix}.cols[$i]", fActual, fExpected))
            }
        }

        if (actual.location != expected.location) {
            result << "${prefix}.location:  $actual.location != $expected.location"
        }

        if (actual.inputFormat != expected.inputFormat) {
            result << "${prefix}.inputFormat:  $actual.inputFormat != $expected.inputFormat"
        }

        if (actual.outputFormat != expected.outputFormat) {
            result << "${prefix}.outputFormat:  $actual.outputFormat != $expected.outputFormat"
        }

        if (actual.compressed != expected.compressed) {
            result << "${prefix}.compressed:  $actual.compressed != $expected.compressed"
        }

        if (actual.numBuckets != expected.numBuckets) {
            result << "${prefix}.numBuckets:  $actual.numBuckets != $expected.numBuckets"
        }

        if (actual.parameters != expected.parameters) {
            result << "${prefix}.parameters:  $actual.parameters != $expected.parameters"
        }

        if (actual.storedAsSubDirectories != expected.storedAsSubDirectories) {
            result << "${prefix}.storedAsSubDirectories:  $actual.storedAsSubDirectories != $expected.storedAsSubDirectories"
        }

        if (actual.serdeInfo.serializationLib != expected.serdeInfo.serializationLib) {
            result << "${prefix}.serdeInfo.serializationLib:  $actual.serdeInfo.serializationLib != $expected.serdeInfo.serializationLib"
        }

        if (actual.serdeInfo.parameters != expected.serdeInfo.parameters) {
            result << "${prefix}.serdeInfo.parameters:  $actual.serdeInfo.parameters != $expected.serdeInfo.parameters"
        }

        // ignoring these for now
        // serdeInfo.name // required
//        private List<String> bucketCols; // required
//        private List<Order> sortCols; // required
//        private SkewedInfo skewedInfo; // optional

        return result
    }

    List<String> tableDifferences(Table a, Table e) {
        if (a == e) {
            return []
        }

        List<String> result = []
        org.apache.hadoop.hive.metastore.api.Table actual = a.getTTable()
        org.apache.hadoop.hive.metastore.api.Table expected = e.getTTable()

        if (actual.tableName != expected.tableName) {
            result << "table.tableName: $actual.tableName != $expected.tableName"
        }

        if (actual.dbName != expected.dbName) {
            result << "table.dbName: $actual.dbName != $expected.dbName"
        }

        if (actual.owner != expected.owner) {
            result << "table.owner: $actual.owner != $expected.owner"
        }

        if (actual.createTime != expected.createTime) {
            result << "table.createTime: $actual.createTime != $expected.createTime"
        }

        if (actual.lastAccessTime != expected.lastAccessTime) {
            result << "table.lastAccessTime: $actual.lastAccessTime != $expected.lastAccessTime"
        }

        if (actual.parameters != expected.parameters) {
            result << "table.parameters: $actual.parameters != $expected.parameters"
        }

        if (actual.retention != expected.retention) {
            result << "table.retention: $actual.retention != $expected.retention"
        }

        if (actual.viewOriginalText != expected.viewOriginalText) {
            result << "table.viewOriginalText: $actual.viewOriginalText != $expected.viewOriginalText"
        }

        if (actual.viewExpandedText != expected.viewExpandedText) {
            result << "table.viewExpandedText: $actual.viewExpandedText != $expected.viewExpandedText"
        }

        if (actual.tableType != expected.tableType) {
            result << "table.tableType: $actual.tableType != $expected.tableType"
        }

        if (actual.temporary != expected.temporary) {
            result << "table.temporary: $actual.temporary != $expected.temporary"
        }

        result.addAll(storageDescriptorDifferences('table.sd', actual.sd, expected.sd))

        for (int i; i < Math.max(actual.partitionKeys.size(), expected.partitionKeys.size()); i++) {
            def fActual = actual.partitionKeys.size() >= i ? actual.partitionKeys[i] : null
            def fExpected = expected.partitionKeys.size() >= i ? expected.partitionKeys[i] : null
            if (!fActual || !fExpected) {
                result << "table.partitionKeys[$i]: $fActual != $fExpected"
            } else {
                result.addAll(fieldSchemaDifferences("table.partitionKeys[$i]", fActual, fExpected))
            }
        }

        // ignoring this for now
//        private PrincipalPrivilegeSet privileges; // optional

        return result
    }

    List<String> partitionDifferences(Partition a, Partition e) {
        if (a == e) {
            return []
        }

        List<String> result = []
        org.apache.hadoop.hive.metastore.api.Partition actual = a.getTPartition()
        org.apache.hadoop.hive.metastore.api.Partition expected = e.getTPartition()

        if (actual.values != expected.values) {
            result << "partition.values: $actual.values != $expected.values"
        }

        if (actual.tableName != expected.tableName) {
            result << "partition.tableName: $actual.tableName != $expected.tableName"
        }

        if (actual.dbName != expected.dbName) {
            result << "partition.dbName: $actual.dbName != $expected.dbName"
        }

        if (actual.createTime != expected.createTime) {
            result << "partition.createTime: $actual.createTime != $expected.createTime"
        }

        if (actual.lastAccessTime != expected.lastAccessTime) {
            result << "partition.lastAccessTime: $actual.lastAccessTime != $expected.lastAccessTime"
        }

        if (actual.parameters != expected.parameters) {
            result << "partition.parameters: $actual.parameters != $expected.parameters"
        }

        result.addAll(storageDescriptorDifferences('partition.sd', actual.sd, expected.sd))

        // ignoring this for now
//        private PrincipalPrivilegeSet privileges; // optional

        return result
    }

    def 'getAllDatabases: returns the same databases for metacat and hive'() {
        given:
        Hive metacat = METASTORES.get(metacatHiveThriftUri)
        Hive hive = METASTORES.get(hiveThriftUri)

        when:
        def metacatDatabases = metacat.getAllDatabases()?.sort()
        def hiveDatabases = hive.getAllDatabases()?.sort()

        then:
        metacatDatabases == hiveDatabases
    }

    def 'createDatabase: can create a database for "#catalog.name"'() {
        given:
        def thrift = METASTORES.get(catalog.thriftUri)
        String databaseName = "db_in_${catalog.name.replace('-', '_')}_$BATCH_ID".toString()
        String description = 'this description will be ignored'
        String locationUri = "file:/tmp/${catalog.name}/${databaseName}".toString()
        def params = ['k1': 'v1', 'k2': 'v2']
        Database db = new Database(databaseName, description, locationUri, params)

        when:
        true

        then:
        !thrift.databaseExists(databaseName)

        when:
        thrift.createDatabase(db)

        then:
        thrift.databaseExists(databaseName)

        when:
        def allDbs = thrift.allDatabases
        def resultDb = thrift.getDatabase(databaseName)

        then:
        allDbs.contains(databaseName)
        resultDb.name == databaseName
        resultDb.description == databaseName // currently the passed description is ignored on create
        resultDb.description != description
        resultDb.locationUri != locationUri // currently the passed location is ignored on create
        !resultDb.parameters
        resultDb.parameters != params // currently the passed params are ignored on created
        catalog.createdDatabases << QualifiedName.ofDatabase(catalog.name, databaseName)

        where:
        catalog << TestCatalogs.getThriftImplementers(TestCatalogs.ALL)
    }

    def 'getDatabase returns the same for metacat and hive after create for #name'() {
        given:
        Hive metacat = METASTORES.get(metacatHiveThriftUri)
        Hive hive = METASTORES.get(hiveThriftUri)

        when:
        def metacatDb = metacat.getDatabase(name.databaseName)
        def hiveDb = hive.getDatabase(name.databaseName)

        then:
        databaseDifferences(metacatDb, hiveDb).empty

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'getDatabasesByPattern: can find #name with multiple patterns'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)

        when:
        def db = thrift.getDatabase(name.databaseName)

        then:
        db != null

        when:
        def dbList = thrift.getDatabasesByPattern('db.*')

        then:
        dbList.contains(name.databaseName)

        when:
        dbList = thrift.getDatabasesByPattern('db.+')

        then:
        dbList.contains(name.databaseName)

        when:
        dbList = thrift.getDatabasesByPattern("db_.+${BATCH_ID}")

        then:
        dbList.contains(name.databaseName)

        when:
        dbList = thrift.getDatabasesByPattern("db_.*${BATCH_ID}")

        then:
        dbList.contains(name.databaseName)

        when:
        dbList = thrift.getDatabasesByPattern(".*_${BATCH_ID}".toString())

        then:
        dbList.contains(name.databaseName)

        when:
        dbList = thrift.getDatabasesByPattern(".+_${BATCH_ID}".toString())

        then:
        dbList.contains(name.databaseName)

        when:
        dbList = thrift.getDatabasesByPattern(".*_${name.catalogName.replace('-', '_')}_.*".toString())

        then:
        dbList.contains(name.databaseName)

        when:
        dbList = thrift.getDatabasesByPattern(".+_${name.catalogName.replace('-', '_')}_.+".toString())

        then:
        dbList.contains(name.databaseName)

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'alterDatabase can alter #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        def now = new Date()

        when:
        def db = thrift.getDatabase(name.databaseName)

        then:
        !db.parameters['field_added']

        when:
        db.parameters['field_added'] = now.toString()
        thrift.alterDatabase(db.name, db)

        then: 'TODO remove this once metacat implements alter_database and uncomment the following lines'
        thrown(Exception)

//        and:
//        db = thrift.getDatabase(name.databaseName)
//
//        then:
//        db.parameters['field_added'] == now.toString()

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'getDatabase returns the same for metacat and hive after alter for #name'() {
        given:
        Hive metacat = METASTORES.get(metacatHiveThriftUri)
        Hive hive = METASTORES.get(hiveThriftUri)

        when:
        def metacatDb = metacat.getDatabase(name.databaseName)
        def hiveDb = hive.getDatabase(name.databaseName)

        then:
        databaseDifferences(metacatDb, hiveDb).empty

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'createTable: can create a partitioned table in #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        def now = new Date()
        def tableName = "partitioned_tbl_$BATCH_ID".toString()
        def owner = 'owner_name'
        def tableParams = ['tp_k1': 'tp_v1']
        def locationUri = "file:/tmp/${name.catalogName}/${name.databaseName}/${tableName}".toString()
        def inputFormat = 'org.apache.hadoop.mapred.TextInputFormat'
        def outputFormat = 'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'
        def sdInfoName = 'is ignored for now'
        def serializationLib = 'org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe'
        def sdInfoParams = ['serialization.format': '1']
        def sdParams = ['sd_k1': 'sd_v1']
        def partitionKeys = [
                new FieldSchema('pk1', 'string', 'pk1 comment'),
                new FieldSchema('pk2', 'bigint', 'pk2 comment')
        ]
        def fields = [
                new FieldSchema('field1', 'string', 'field1 comment'),
                new FieldSchema('field2', 'boolean', 'field2 comment'),
                new FieldSchema('field3', 'bigint', 'field3 comment'),
                new FieldSchema('field4', 'double', 'field4 comment'),
                new FieldSchema('field5', 'binary', 'field5 comment'),
                new FieldSchema('field6', 'date', 'field6 comment'),
                new FieldSchema('field7', 'timestamp', 'field7 comment'),
                new FieldSchema('field8', 'array<boolean>', 'field8 comment'),
                new FieldSchema('field9', 'map<string,string>', 'field9 comment'),
        ]

//        int createTime,
//        int lastAccessTime,
//        int retention,
//        String tableType)
        def table = new Table(name.databaseName, tableName)
        table.owner = owner
        table.setParamters(tableParams)
        table.setPartCols(partitionKeys)

//        boolean compressed,
//        int numBuckets,
//        List<String> bucketCols,
//        List<Order> sortCols,
        table.sd.cols = fields
        table.sd.location = locationUri
        table.sd.inputFormat = inputFormat
        table.sd.outputFormat = outputFormat
        table.sd.serdeInfo = new SerDeInfo(sdInfoName, serializationLib, sdInfoParams)
        table.sd.parameters = sdParams

        when:
        def tables = thrift.getAllTables(name.databaseName)

        then:
        !tables.contains(tableName)

        when:
        thrift.createTable(table)
        tables = thrift.getAllTables(name.databaseName)

        then:
        tables.contains(table.tableName)

        when:
        def resultTbl = thrift.getTable(table.dbName, table.tableName)

        then:
        resultTbl.tableName == tableName
        resultTbl.dbName == name.databaseName
        resultTbl.owner == owner
        DateUtilities.epochCloseEnough(resultTbl.getTTable().createTime, now, 60)
        !resultTbl.lastAccessTime
        !resultTbl.retention
        resultTbl.tableType == TableType.EXTERNAL_TABLE
        resultTbl.parameters['EXTERNAL'] == 'TRUE'
        tableParams.keySet().every { tableParams[it] == resultTbl.parameters[it] }
        resultTbl.partitionKeys == partitionKeys
        !resultTbl.sd.compressed
        !resultTbl.sd.numBuckets
        !resultTbl.bucketCols
        !resultTbl.sortCols
        resultTbl.sd.cols == fields
        resultTbl.sd.location == locationUri
        resultTbl.sd.inputFormat == inputFormat
        resultTbl.sd.outputFormat == outputFormat
        !resultTbl.sd.serdeInfo.name
        resultTbl.sd.serdeInfo.serializationLib == serializationLib
        resultTbl.sd.serdeInfo.parameters == sdInfoParams
        resultTbl.sd.parameters == sdParams
        catalog.createdTables << QualifiedName.ofTable(name.catalogName, name.databaseName, tableName)

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'createTable: can create a unpartitioned table in #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        def now = new Date()
        def tableName = "unpartitioned_tbl_$BATCH_ID".toString()
        def owner = 'owner_name'
        def tableParams = ['tp_k1': 'tp_v1']
        def locationUri = "file:/tmp/${name.catalogName}/${name.databaseName}/${tableName}".toString()
        def inputFormat = 'org.apache.hadoop.mapred.TextInputFormat'
        def outputFormat = 'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'
        def sdInfoName = 'is ignored for now'
        def serializationLib = 'org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe'
        def sdInfoParams = ['serialization.format': '1']
        def sdParams = ['sd_k1': 'sd_v1']
        def fields = [
                new FieldSchema('field1', 'string', 'field1 comment'),
                new FieldSchema('field2', 'boolean', 'field2 comment'),
                new FieldSchema('field3', 'bigint', 'field3 comment'),
                new FieldSchema('field4', 'double', 'field4 comment'),
                new FieldSchema('field5', 'binary', 'field5 comment'),
                new FieldSchema('field6', 'date', 'field6 comment'),
                new FieldSchema('field7', 'timestamp', 'field7 comment'),
                new FieldSchema('field8', 'array<boolean>', 'field8 comment'),
                new FieldSchema('field9', 'map<string,string>', 'field9 comment'),
        ]

//        int createTime,
//        int lastAccessTime,
//        int retention,
//        String tableType)
        def table = new Table(name.databaseName, tableName)
        table.owner = owner
        table.setParamters(tableParams)

//        boolean compressed,
//        int numBuckets,
//        List<String> bucketCols,
//        List<Order> sortCols,
        table.sd.cols = fields
        table.sd.location = locationUri
        table.sd.inputFormat = inputFormat
        table.sd.outputFormat = outputFormat
        table.sd.serdeInfo = new SerDeInfo(sdInfoName, serializationLib, sdInfoParams)
        table.sd.parameters = sdParams

        when:
        def tables = thrift.getAllTables(name.databaseName)

        then:
        !tables.contains(tableName)

        when:
        thrift.createTable(table)
        tables = thrift.getAllTables(name.databaseName)

        then:
        tables.contains(table.tableName)

        when:
        def resultTbl = thrift.getTable(table.dbName, table.tableName)

        then:
        resultTbl.tableName == tableName
        resultTbl.dbName == name.databaseName
        resultTbl.owner == owner
        DateUtilities.epochCloseEnough(resultTbl.getTTable().createTime, now, 60)
        !resultTbl.lastAccessTime
        !resultTbl.retention
        resultTbl.tableType == TableType.EXTERNAL_TABLE
        resultTbl.parameters['EXTERNAL'] == 'TRUE'
        tableParams.keySet().every { tableParams[it] == resultTbl.parameters[it] }
        !resultTbl.partitionKeys
        !resultTbl.sd.compressed
        !resultTbl.sd.numBuckets
        !resultTbl.bucketCols
        !resultTbl.sortCols
        resultTbl.sd.cols == fields
        resultTbl.sd.location == locationUri
        resultTbl.sd.inputFormat == inputFormat
        resultTbl.sd.outputFormat == outputFormat
        !resultTbl.sd.serdeInfo.name
        resultTbl.sd.serdeInfo.serializationLib == serializationLib
        resultTbl.sd.serdeInfo.parameters == sdInfoParams
        resultTbl.sd.parameters == sdParams
        catalog.createdTables << QualifiedName.ofTable(name.catalogName, name.databaseName, tableName)

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'getTable returns the same for metacat and hive after table create for #name'() {
        given:
        Hive metacat = METASTORES.get(metacatHiveThriftUri)
        Hive hive = METASTORES.get(hiveThriftUri)

        when:
        def metacatTbl = metacat.getTable(name.databaseName, name.tableName)
        def hiveTbl = hive.getTable(name.databaseName, name.tableName)

        then:
        tableDifferences(metacatTbl, hiveTbl).empty

        where:
        name << TestCatalogs.getCreatedTables(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'createPartition: fails on an unpartitioned table #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        def table = new Table(name.databaseName, name.tableName)

        when:
        def tables = thrift.getAllTables(name.databaseName)

        then:
        tables.contains(name.tableName)

        when:
        thrift.createPartition(table, ['pk1': 'partition_value', 'pk2': '1'])

        then:
        def e = thrown(HiveException)

        where:
        name << TestCatalogs.getCreatedTables(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
                .findAll { it.tableName.startsWith('unpartitioned') }
    }

    def 'createPartition: create partition "#testCriteria.pspec" on table "#testCriteria.table"'() {
        given:
        def name = testCriteria.table as QualifiedName
        def partitionSpec = testCriteria.pspec as Map<String, String>
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)

        when:
        def table = thrift.getTable(name.databaseName, name.tableName)

        then:
        table != null

        when:
        def partition = thrift.createPartition(table, partitionSpec)

        then:
        partition.values == [partitionSpec['pk1'], partitionSpec['pk2']]

        when:
        def partNames = thrift.getPartitionNames(name.databaseName, name.tableName, Short.MAX_VALUE)

        then:
        partNames.contains(partition.name)
        catalog.createdPartitions <<
                QualifiedName.ofPartition(name.catalogName, name.databaseName, name.tableName, partition.name)

        where:
        testCriteria << TestCatalogs.getCreatedTables(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
                .findAll { it.tableName.startsWith('partitioned') }
                .collect { partitionedTable ->
            [
                    ['pk1': 'value with space', 'pk2': '0'],
                    ['pk1': 'CAP', 'pk2': '0'],
                    ['pk1': 'ALL_CAP', 'pk2': '0'],
                    ['pk1': 'lower', 'pk2': '0'],
                    ['pk1': 'camelCase', 'pk2': '0'],
                    // TODO handle escaping
//                    ['pk1': 'http://example.com/path?queryName=queryValue&q2=qv2', 'pk2': String.valueOf(Long.MAX_VALUE)],
//                    ['pk1': 'weird=true', 'pk2': String.valueOf(Long.MIN_VALUE)],
//                    ['pk1': 'weird:value', 'pk2': '0'],

            ].collect { spec ->
                return [table: partitionedTable, pspec: spec]
            }
        }
        .flatten()
    }

    def 'createPartitions: creates multiple partitions on table #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        def toAdd = new AddPartitionDesc(name.databaseName, name.tableName, false)
        toAdd.addPartition(['pk1': 'key1_val1', 'pk2': '0'], 'add_part_1')
        toAdd.addPartition(['pk1': 'key1_val1', 'pk2': '1'], 'add_part_2')
        toAdd.addPartition(['pk1': 'key1_val2', 'pk2': '2'], 'add_part_3')

        when:
        def table = thrift.getTable(name.databaseName, name.tableName)

        then:
        table != null

        when:
        List<Partition> partitions = thrift.createPartitions(toAdd)

        then:
        partitions.size() == 3
        partitions[0].values == ['key1_val1', '0']
        partitions[0].location.endsWith('/add_part_1')
        catalog.createdPartitions <<
                QualifiedName.ofPartition(name.catalogName, name.databaseName, name.tableName, partitions[0].name)
        partitions[1].values == ['key1_val1', '1']
        partitions[1].location.endsWith('/add_part_2')
        catalog.createdPartitions <<
                QualifiedName.ofPartition(name.catalogName, name.databaseName, name.tableName, partitions[1].name)
        partitions[2].values == ['key1_val2', '2']
        partitions[2].location.endsWith('/add_part_3')
        catalog.createdPartitions <<
                QualifiedName.ofPartition(name.catalogName, name.databaseName, name.tableName, partitions[2].name)

        where:
        name << TestCatalogs.getCreatedTables(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
                .findAll { it.tableName.startsWith('partitioned') }
    }

    def 'getPartitions can be used to find #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        Map<String, String> partitionSpec = Warehouse.makeSpecFromName(name.partitionName)

        when:
        def table = thrift.getTable(name.databaseName, name.tableName)

        then:
        table != null

        when:
        def partitions = thrift.getPartitions(table)
        def match = partitions?.find { it.name == name.partitionName }

        then:
        partitions != null
        !partitions.empty
        match != null

        when:
        def specCopy = partitionSpec.clone() as Map<String, String>
        specCopy['pk1'] = ''
        partitions = thrift.getPartitions(table, specCopy)
        match = partitions?.find { it.name == name.partitionName }

        then:
        partitions != null
        !partitions.empty
        match != null

        when:
        partitions = thrift.getPartitions(table, partitionSpec, 1 as Short)
        match = partitions?.find { it.name == name.partitionName }

        then:
        partitions != null
        partitions.size() == 1
        match != null

        when:
        String filter = """pk1 = "${partitionSpec['pk1']}" OR pk1 = "${partitionSpec['pk1']}" """.trim()
        partitions = thrift.getPartitionsByFilter(table, filter)
        match = partitions?.find { it.name == name.partitionName }

        then:
        partitions != null
        !partitions.empty
        match != null

        when:
        partitions = thrift.getPartitionsByNames(table, [name.partitionName])
        match = partitions?.find { it.name == name.partitionName }

        then:
        partitions != null
        partitions.size() == 1
        match != null

        when:
        partitions = thrift.getPartitionsByNames(table, partitionSpec)
        match = partitions?.find { it.name == name.partitionName }

        then:
        partitions != null
        partitions.size() == 1
        match != null

        where:
        name << TestCatalogs.getCreatedPartitions(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'test partitionSpec filtering does AND not OR filtering in db #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        def tableName = "test_0129_$BATCH_ID".toString()
        def partitionKeys = [
                new FieldSchema('pk1', 'string', 'pk1 comment'),
                new FieldSchema('pk2', 'bigint', 'pk2 comment'),
                new FieldSchema('pk3', 'bigint', 'pk2 comment')
        ]
        def fields = [
                new FieldSchema('field1', 'string', 'field1 comment'),
        ]

        def table = new Table(name.databaseName, tableName)
        table.setPartCols(partitionKeys)
        table.sd.cols = fields

        when:
        def tables = thrift.getAllTables(name.databaseName)

        then:
        !tables.contains(tableName)

        when:
        thrift.createTable(table)
        tables = thrift.getAllTables(name.databaseName)

        then:
        tables.contains(table.tableName)

        when: 'the table is reloaded'
        table = thrift.getTable(table.dbName, table.tableName)

        then:
        table.tableName == tableName

        when: 'data is added'
        (0..15).each { i ->
            thrift.createPartition(table, [
                    'pk1': i % 2 == 0 ? 'even' : 'odd',
                    'pk2': Integer.toString(i),
                    'pk3': Integer.toString(i % 2)
            ])
        }
        def partitions = thrift.getPartitionNames(name.databaseName, tableName, (short) -1)

        then:
        partitions.size() == 16

        and:
        def f = { Map<String, String> pspec ->
            thrift.getPartitions(table, pspec)
        }.memoize()

        expect:
        f(['pk1': 'even']).size() == 8
        f(['pk1': 'even']).every { Partition partition -> partition.values.first() == 'even' }
        f(['pk1': 'odd']).size() == 8
        f(['pk1': 'odd']).every { Partition partition -> partition.values.last() == '1' }
        f(['pk1': 'even', 'pk3': '1']).size() == 0
        f(['pk1': 'odd', 'pk3': '0']).size() == 0
        f(['pk1': 'even', 'pk3': '0']).size() == 8
        f(['pk1': 'even', 'pk3': '0']).every { Partition partition -> partition.values.first() == 'even' && partition.values.last() == '0' }
        f(['pk1': 'odd', 'pk3': '1']).size() == 8
        f(['pk1': 'odd', 'pk3': '1']).every { Partition partition -> partition.values.first() == 'odd' && partition.values.last() == '1' }
        f(['pk1': 'odd', 'pk2': '7']).size() == 1
        f(['pk1': 'odd', 'pk2': '7']).first().values == ['odd', '7', '1']
        f(['pk1': 'odd', 'pk2': '8']).size() == 0
        f(['pk1': 'even', 'pk2': '7']).size() == 0
        f(['pk1': 'even', 'pk2': '8']).size() == 1
        f(['pk1': 'even', 'pk2': '8']).first().values == ['even', '8', '0']
        f(['pk3': '1', 'pk2': '7']).size() == 1
        f(['pk3': '1', 'pk2': '7']).first().values == ['odd', '7', '1']
        f(['pk3': '1', 'pk2': '8']).size() == 0
        f(['pk3': '0', 'pk2': '7']).size() == 0
        f(['pk3': '0', 'pk2': '8']).size() == 1
        f(['pk3': '0', 'pk2': '8']).first().values == ['even', '8', '0']

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'getPartition returns the same for metacat and hive after partition create for #name'() {
        given:
        Hive metacat = METASTORES.get(metacatHiveThriftUri)
        Hive hive = METASTORES.get(hiveThriftUri)

        when:
        def metacatTbl = metacat.getTable(name.databaseName, name.tableName)
        def hiveTbl = hive.getTable(name.databaseName, name.tableName)

        then:
        [hiveTbl, metacatTbl].every()

        when:
        def metacatPartitions = metacat.getPartitions(metacatTbl)
        def hivePartitions = metacat.getPartitions(hiveTbl)
        def metacatPartition = metacatPartitions?.find { it.name == name.partitionName }
        def hivePartition = hivePartitions?.find { it.name == name.partitionName }

        then:
        [hivePartition, metacatPartition].every()
        partitionDifferences(metacatPartition, hivePartition).empty

        where:
        name << TestCatalogs.getCreatedPartitions(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'dropPartition: can drop #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        Map<String, String> partitionSpec = Warehouse.makeSpecFromName(name.partitionName)

        when:
        def table = thrift.getTable(name.databaseName, name.tableName)

        then:
        table != null

        when:
        def partition = thrift.getPartition(table, partitionSpec, false)

        then:
        partition != null

        when:
        def result = thrift.dropPartition(name.databaseName, name.tableName, partition.values, false)

        then:
        result

        when:
        partition = thrift.getPartition(table, partitionSpec, false)

        then:
        partition == null
        catalog.createdPartitions.remove(name)

        where:
        name << TestCatalogs.getCreatedPartitions(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
        // TODO add this back when we handle encoding
//        name << TestCatalogs.getCreatedPartitions(TestCatalogs.getThriftImplementers(TestCatalogs.ALL)).findAll {
//            !this.needsDecoding(it.partitionName)
//        }
    }

    // TODO add this back when we handle encoding
    @Ignore
    def 'dropPartition: currently fails for encoded partition #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)
        Map<String, String> partitionSpec = Warehouse.makeSpecFromName(name.partitionName)

        when:
        def table = thrift.getTable(name.databaseName, name.tableName)

        then:
        table != null

        when:
        def partition = thrift.getPartition(table, partitionSpec, false)

        then:
        partition != null

        when:
        thrift.dropPartition(name.databaseName, name.tableName, partition.values, false)

        then:
        // TODO this should not throw an exception
        def e = thrown(HiveException)
        e.message.contains("Partition or table doesn't exist")

        where:
        name << TestCatalogs.getCreatedPartitions(TestCatalogs.getThriftImplementers(TestCatalogs.ALL)).findAll {
            this.needsDecoding(it.partitionName)
        }
    }

    def 'dropTable: can drop #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)

        when:
        def table = thrift.getTable(name.databaseName, name.tableName)

        then:
        table != null

        when:
        thrift.dropTable(name.databaseName, name.tableName, false, false, false)
        thrift.getTable(name.databaseName, name.tableName)

        then:
        def e = thrown(InvalidTableException)
        e.message.contains('Table not found')
        catalog.createdTables.remove(name)

        where:
        name << TestCatalogs.getCreatedTables(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }

    def 'dropDatabase: can drop #name'() {
        given:
        def catalog = TestCatalogs.findByQualifiedName(name)
        def thrift = METASTORES.get(catalog.thriftUri)

        when:
        def database = thrift.getDatabase(name.databaseName)

        then:
        database != null

        when:
        thrift.dropDatabase(name.databaseName, false, false, false)

        then:
        def e = thrown(HiveException)
        e.cause instanceof InvalidOperationException
        e.message.contains('Not implemented yet: drop_database')

        // dropDatabase isn't implemented yet
//        database = thrift.getDatabase(name.databaseName)
//
//        then:
//        database == null
//        catalog.createdDatabases.remove(name)

        where:
        name << TestCatalogs.getCreatedDatabases(TestCatalogs.getThriftImplementers(TestCatalogs.ALL))
    }
}