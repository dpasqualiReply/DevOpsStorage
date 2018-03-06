package it.reply.data.pasquali

import java.io.File

import com.typesafe.config.ConfigFactory
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.kudu.spark.kudu._
import org.apache.spark.sql._
import org.zeroturnaround.zip.ZipUtil

import scala.sys.process._

case class Storage() {

  val defaultThrift : String = "thrift://localhost:9083"

  var kuduMaster : String = "cloudera-vm.c.endless-upgrade-187216.internal"
  var kuduPort : String = "7051"
  var KUDU = ""

  var KUDU_TABLE_BASE = ""

  var hdfsServer : String = "cloudera-vm.c.endless-upgrade-187216.internal"
  var hdfsPort : String = "8020"
  val HDFS = s"${hdfsServer}:${hdfsPort}"

  var kuduContext : KuduContext = null
  var spark : SparkSession = null

  var hdfs : FileSystem = null


  def init(withHive : Boolean) : Storage = {
    init("local[*]", "Movie Recommender", withHive)
  }

  def init(master : String, appName : String, withHive: Boolean) : Storage = {

    if(withHive)
      spark = SparkSession.builder().master(master).appName(appName).enableHiveSupport().getOrCreate()
    else
      spark = SparkSession.builder().master(master).appName(appName).getOrCreate()
    this
  }

  def init(master : String, appName : String, hiveThriftServer: String, hiveThriftPort : String) : Storage = {

    spark = SparkSession
      .builder()
      .master(master)
      .appName(appName)
      .config("hive.metastore.uris", s"thrift://${hiveThriftServer}:${hiveThriftPort}")
      .enableHiveSupport().getOrCreate()

    this
  }

  def initKudu(master : String, port : String, baseTable : String) : Storage = {

    kuduMaster = master
    kuduPort = port
    KUDU = s"${kuduMaster}:${kuduPort}"
    kuduContext = new KuduContext(KUDU, spark.sparkContext)
    KUDU_TABLE_BASE = baseTable

    this
  }

  def initHDFS(server : String, port : String) : Storage = {

    hdfsServer = server
    hdfsPort = port

    val hadoopConfig = new Configuration()
    hadoopConfig.set("fs.defaultFS", HDFS)
    hdfs = FileSystem.get(hadoopConfig)

    this
  }

  def upsertKuduRows(rows : DataFrame, table : String) : Unit = {

    val tableName = s"${KUDU_TABLE_BASE}${table}"
    kuduContext.upsertRows(rows, tableName)

  }

  def updateKuduRows(rows : DataFrame, table : String) : Unit = {

    val tableName = s"${KUDU_TABLE_BASE}${table}"
    kuduContext.updateRows(rows, tableName)

  }

  def deleteKuduRows(keys : DataFrame, table : String) : Unit = {

    val tableName = s"${KUDU_TABLE_BASE}${table}"
    kuduContext.deleteRows(keys, tableName)

  }

  def readKuduTable(kuduTestTable: String): DataFrame ={

    val df = spark.sqlContext.read.options(
      Map(
        "kudu.master" -> KUDU,
        "kudu.table" -> s"${KUDU_TABLE_BASE}${kuduTestTable}"
      )
    ).kudu

    df
  }

  def storeHDFSFile(filePath : String, fileName : String, data : Array[Byte]) : Unit = {

    val path = new Path(filePath)

    if(!hdfs.exists(path))
      hdfs.mkdirs(path)

    val fullPath = new Path(filePath+"/"+fileName)

    hdfs.create(fullPath).write(data)
  }

  def readHDFSFile(filePath : String) : Array[Byte] = {
    return null
  }

  def readHiveTable(table : String) : DataFrame = {
    spark.sql(s"select * from $table")
  }

  def runHiveQuery(query : String) : DataFrame = {
    spark.sql(query)
  }

  def writeDFtoHive(df : DataFrame, mode : String, db : String, table : String) : Unit = {
    df.write.mode(mode).saveAsTable(s"${db}.${table}")
  }

  def remoteSecureCopy(inputFile : String,
                       remoteUser : String, remoteHost : String, remotePath : String) : String = {

    s"scp -v ${inputFile} ${remoteHost}@${remoteHost}:/${remotePath}" !!
  }

  def zipModel(sourceDir : String, outName : String) : Unit = {
    ZipUtil.pack(new File(sourceDir), new File(outName))
  }

  def unzipModel(zipFile : String, outDir : String) : Unit = {
    ZipUtil.unpack(new File(zipFile), new File(outDir))
  }

  def closeSession() : Unit = {

    if(hdfs != null){
      hdfs.close()
      hdfs = null
    }

    if(spark != null) {
      spark.close()
      spark = null
    }

    kuduContext = null
  }







}
