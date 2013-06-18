流程分析，按先后顺序

1 HMaster构造函数

  HRegionServer构造函数, HRegionServer的RPC端口默认是60020，master的RPC端口默认是60000
  HRegionServer的Jetty(InfoServer)端口默认是60030，master的Jetty(InfoServer)端口默认是60010

  1.1 获取当前运行HMaster的机器的地址
  1.2 生成HBaseServer对象用于接收RPC请求，并启动HBaseServer的相关线程
  1.3 生成ZooKeeperWatcher对象
      在构造函数中生成这些持久结点: /hbase, /hbase/unassigned, /hbase/rs, /hbase/table, /hbase/splitlog

	ZooKeeperWatcher管理下面10个结点:

	baseZNode              "/hbase"
	rootServerZNode        "/hbase/root-region-server"
	rsZNode                "/hbase/rs"
	drainingZNode          "/hbase/draining"
	masterAddressZNode     "/hbase/master"
	clusterStateZNode      "/hbase/shutdown"
	assignmentZNode        "/hbase/unassigned"
	tableZNode             "/hbase/table"
	clusterIdZNode         "/hbase/hbaseid"
	splitLogZNode          "/hbase/splitlog"
	schemaZNode            "/hbase/schema"

	这6个结点在ZooKeeperWatcher构造函数中生成
	baseZNode              "/hbase"
	rsZNode                "/hbase/rs"
	drainingZNode          "/hbase/draining"
	assignmentZNode        "/hbase/unassigned"
	tableZNode             "/hbase/table"
	splitLogZNode          "/hbase/splitlog"
	schemaZNode            "/hbase/schema"

	这4个在不同地方生成
	rootServerZNode        "/hbase/root-region-server"
	masterAddressZNode     "/hbase/master" //在HMaster中建立，并且是一个短暂结点，结点的值是HMaster的ServerName
	                                       //见org.apache.hadoop.hbase.master.ActiveMasterManager.blockUntilBecomingActiveMaster
	clusterStateZNode      "/hbase/shutdown"
	clusterIdZNode         "/hbase/hbaseid" //在HMaster.finishInitialization方法中调用ClusterId.setClusterId建立，结点值是UUID

  1.4 生成MasterMetrics对象

2 HMaster.run
  
  2.1
  生成ActiveMasterManager对象，如果此HMaster作为一个备份(backup)，
  那么需要等到集群中有Active Master时才往下调用blockUntilBecomingActiveMaster，
  并且调用blockUntilBecomingActiveMaster也会阻塞，直到它变成ActiveMaster。

  与此同时，在blockUntilBecomingActiveMaster中会创建短暂结点"/hbase/master"，
  此节点的值是HMaster的版本化ServerName(也就是version+ServerName)，
  此结点用于协调region server的启动，只有"/hbase/master"创建好后，region server才能往下进行。
 
  2.2
  调用HMaster.finishInitialization

    2.2.1
	生成MasterFileSystem对象
	建立由hbase-site.xml的hbase.rootdir属性指定的目录(如:file:/E:/hbase/data)
	调用FSUtils.setVersion在hbase.rootdir目录中建立一个hbase.version文件，并写入版本号(HConstants.FILE_SYSTEM_VERSION=7)
	判断-ROOT-分区是否存在，不存在则调用MasterFileSystem.bootstrap来创新-ROOT-和.META.

	最后创建file:/E:/hbase/data/.oldlogs目录

	2.2.2
	如果持久结点"/hbase/hbaseid"不存在则创建它，否则不创建，同时每次master启动时都会把此节点的值设为hbase.id文件中的值

	2.2.3
	生成ExecutorService (TODO)

	2.2.4
	生成ServerManager (TODO)

	2.2.5
	initializeZKBasedSystemTrackers

	  2.2.5.1
	  生成CatalogTracker, 它包含两个ZooKeeperNodeTracker，分别是RootRegionTracker和MetaNodeTracker，
	  对应/hbase/root-region-server和/hbase/unassigned/1028785192这两个结点(1028785192是.META.的分区名)
	  如果之前从未启动过hbase，那么在start CatalogTracker时这两个结点不存在。
	  /hbase/root-region-server是一个持久结点，在RootLocationEditor中建立

	  2.2.5.2
	  生成AssignmentManager 

	  2.2.5.3
	  生成 LoadBalancer

	  2.2.5.4
	  生成 RegionServerTracker: 监控"/hbase/rs"结点

	  2.2.5.5
	  生成 DrainingServerTracker: 监控"/hbase/draining"结点

	  2.2.5.6
	  生成 ClusterStatusTracker，通过它的setClusterUp方法创建持久结点"/hbase/shutdown"，结点值是当前时间，
	  如果结点已存在(master可能未正常关闭)，那么此结点的值不更新。

	
	2.2.6
	生成 MasterCoprocessorHost

	2.2.7
	startServiceThreads()

	启动服务线程
	(MASTER_OPEN_REGION、MASTER_CLOSE_REGION、MASTER_SERVER_OPERATIONS、MASTER_META_SERVER_OPERATIONS、MASTER_TABLE_OPERATIONS
	这几个只是生成Executor，并未正式启动, 正式启动的有LogCleaner，和基于Jetty的InfoServer(端口号默认是60010))
	
	2.2.8
	等待RegionServer注册

	2.2.9
	splitLogAfterStartup (TODO)

	2.2.10
	assignRootAndMeta
		
		2.2.10.1
		processRegionInTransitionAndBlockUntilAssigned
		先看一下分区正在转换状态当中，如果处于转换状态当中则先处理相关的状态，并等待体处理结束后再往下进行。

		2.2.10.2
		verifyRootRegionLocation

		2.2.10.3
		getRootLocation

		2.2.10.4.A
		expireIfOnline

		2.2.10.4.B
		assignRoot

		先删掉"/hbase/root-region-server",不管它存不存在，KeeperException.NoNodeException被忽略了

		写入EventType.M_ZK_REGION_OFFLINE、当前时间戳、跟分区名(-ROOT-,,0)、master的版本化ServerName
		到/hbase/unassigned/70236052, payload为null，所以不写入

		RegionServer修改/hbase/unassigned/70236052的值，
		写入EventType.RS_ZK_REGION_OPENING、当前时间戳、跟分区名(-ROOT-,,0)、RegionServer的版本化ServerName

	
	2.2.11
	MetaMigrationRemovingHTD.updateMetaWithNewHRI

	2.2.12
	assignmentManager.joinCluster()
	把meta表中的分区读出来，然后分配到Region Server,
	meta表只有一个列族：info，存入meta的行有三列: 
	regioninfo、server、serverstartcode，
	其中regioninfo对应HRegionInfo，
	server对应ServerName的host和port
	serverstartcode对应ServerName的startcode(一般是时间戳)。

		2.2.12.1
		rebuildUserRegions()
			
			2.2.12.1.1
			调用MetaReader.fullScan 从meta表中取出所有的分区，得到一个List<Result>，
			调用MetaReader.parseCatalogResult，解析每个result得到Pair<HRegionInfo, ServerName>，
			其中HRegionInfo由regioninfo列的值反序列化得来，ServerName由server、serverstartcode两列的值反序列化后组合而成。

		2.2.12.2
		processDeadServersAndRegionsInTransition

