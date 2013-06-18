1.
在HMaster.finishInitialization方法中触发MasterFileSystem的构造函数

2. MasterFileSystem的构造函数做的事:
   
   hbase-site.xml文件中定义了一个"hbase.rootdir"属性，这个属性不能缺少，
   否则会出这样的错:
   java.lang.IllegalArgumentException: Can not create a Path from a null string
	at org.apache.hadoop.fs.Path.checkPathArg(Path.java:75)
	at org.apache.hadoop.fs.Path.<init>(Path.java:85)
	at org.apache.hadoop.hbase.util.FSUtils.getRootDir(FSUtils.java:451)


	MasterFileSystem的构造函数中先取出这个属性的值，保存到Path rootdir这个字段中

	这是windows下的配置样例:
	<property>
		<name>hbase.rootdir</name>
		<value>file:/E:/hbase/data</value>
	</property>


	然后按照此变量和当前配置(Configuration)，得到一个org.apache.hadoop.fs.FileSystem
	如果没用到HDFS，像上面就会是org.apache.hadoop.fs.LocalFileSystem，
	然后根据此FileSystem得到File System Uri,对于上面的FileSystem是"file:///"，把这个值保
	存到Configuration的两个属性中(分别是"fs.default.name"和"fs.defaultFS",如果配置文件中已有，这里将会覆盖它)


	接着读取"hbase.master.distributed.log.splitting"属性的值，默认distributedLogSplitting为true，
	表示启用SplitLogManager，构造一个SplitLogManager实例:


	createInitialFileSystemLayout()
		
		checkRootDir

			1. 等待fs退出安全模式(默认10秒钟轮循一次，可通过参数hbase.server.thread.wakefrequency调整

			2.a. 如果hbase.rootdir目录不存在则创建它，
			     然后在此目录中创建名为"hbase.version"的文件，内容是文件系统版本号("7")

			2.b. 如果hbase.rootdir目录已存在，则读出"hbase.version"文件的内容与当前的版本号相比，
			如果不相等，则打印错误信息(提示版本不对)，抛出异常FileSystemVersionException

			3. 检查hbase.rootdir目录下是否有名为"hbase.id"的文件，如果没有则创建它，
			内容是随机生成的UUID(总长度36位，由5部份组成，用"-"分隔)，如: 6c43f934-37a2-4cae-9d49-3f5abfdc113d

			4. 读出"hbase.id"的文件的内容存到clusterId字段

			5. 判断hbase.rootdir目录中是否有"-ROOT-/70236052"目录，没有的话说明是第一次启动hbase，进入:

				5.1 bootstrap(final Path rd, final Configuration c)

					调用HRegion.createHRegion建立"-ROOT-"分区和".META."分区时，目录布局如下
					E:\HBASE\DATA
					│  .hbase.id.crc
					│  .hbase.version.crc
					│  hbase.id
					│  hbase.version
					│
					├─-ROOT-
					│  └─70236052
					│      │  ..regioninfo.crc
					│      │  .regioninfo
					│      │
					│      ├─.logs
					│      │      .hlog.1329045483158.crc
					│      │      hlog.1329045483158
					│      │
					│      ├─.oldlogs
					│      └─info
					└─.META
						└─1028785192
							│  ..regioninfo.crc
							│  .regioninfo
							│
							├─.logs
							│      .hlog.1329045485940.crc
							│      hlog.1329045485940
							│
							├─.oldlogs
							└─info

					把".META."分区信息加到"-ROOT-"表，并关闭分区和hlog时
					E:\HBASE\DATA
					│  .hbase.id.crc
					│  .hbase.version.crc
					│  hbase.id
					│  hbase.version
					│
					├─-ROOT-  //"-ROOT-"表名
					│  └─70236052 //"-ROOT-"分区名
					│      │  ..regioninfo.crc
					│      │  .regioninfo //"-ROOT-"分区描述表件
					│      │
					│      ├─.oldlogs
					│      │      .hlog.1329045483158.crc
					│      │      hlog.1329045483158
					│      │
					│      ├─.tmp
					│      └─info  //列族名
					│              .c4d7a00bb555409f9a4a8b4fbc57f1bd.crc
					│              c4d7a00bb555409f9a4a8b4fbc57f1bd       //存放".META."分区信息的StoreFile
					│
					└─.META
						└─1028785192
							│  ..regioninfo.crc
							│  .regioninfo
							│
							├─.oldlogs
							│      .hlog.1329045485940.crc
							│      hlog.1329045485940
							│
							└─info



				5.2  createRootTableInfo 建立"-ROOT-"表的描述文件

					判断hbase.rootdir/-ROOT-目录中是否存在.tableinfo开头的文件

					
			6. 创建file:/E:/hbase/data/.oldlogs目录


	执行完MasterFileSystem构造函数时的目录结构如下:

		E:\HBASE\DATA
		│  .hbase.id.crc
		│  .hbase.version.crc
		│  hbase.id
		│  hbase.version
		│
		├─-ROOT-
		│  │  ..tableinfo.0000000001.crc
		│  │  .tableinfo.0000000001
		│  │
		│  ├─.tmp
		│  └─70236052
		│      │  ..regioninfo.crc
		│      │  .regioninfo
		│      │
		│      ├─.oldlogs
		│      │      .hlog.1329045483158.crc
		│      │      hlog.1329045483158
		│      │
		│      ├─.tmp
		│      └─info
		│              .c4d7a00bb555409f9a4a8b4fbc57f1bd.crc
		│              c4d7a00bb555409f9a4a8b4fbc57f1bd
		│
		├─.META
		│  └─1028785192
		│      │  ..regioninfo.crc
		│      │  .regioninfo
		│      │
		│      ├─.oldlogs
		│      │      .hlog.1329045485940.crc
		│      │      hlog.1329045485940
		│      │
		│      └─info
		└─.oldlogs


