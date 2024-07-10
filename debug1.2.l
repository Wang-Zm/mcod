src/mtree/MTree.java:757: 警告: '_' 用作标识符
				IndexItem _ = thisNode.children.remove(child.data);
				          ^
  (Java SE 8 之后的发行版中可能不支持使用 '_' 作为标识符)
src/mtree/MTree.java:758: 警告: '_' 用作标识符
				assert _ != null;
				       ^
  (Java SE 8 之后的发行版中可能不支持使用 '_' 作为标识符)
src/mtree/MTree.java:805: 警告: '_' 用作标识符
						IndexItem _ = thisNode.children.remove(existingChild.data);
						          ^
  (Java SE 8 之后的发行版中可能不支持使用 '_' 作为标识符)
src/mtree/MTree.java:806: 警告: '_' 用作标识符
						assert _ != null;
						       ^
  (Java SE 8 之后的发行版中可能不支持使用 '_' 作为标识符)
src/mtree/MTree.java:907: 警告: '_' 用作标识符
				IndexItem _ = nearestDonor.children.remove(nearestGrandchild.data);
				          ^
  (Java SE 8 之后的发行版中可能不支持使用 '_' 作为标识符)
src/mtree/MTree.java:908: 警告: '_' 用作标识符
				assert _ != null;
				       ^
  (Java SE 8 之后的发行版中可能不支持使用 '_' 作为标识符)
注: src/mtree/tests/MTTest.java使用或覆盖了已过时的 API。
注: 有关详细信息, 请使用 -Xlint:deprecation 重新编译。
6 个警告
processing gau, vary window, window = 50000
mtree.MTree$DataNotFound
	at mtree.MTree$NonLeafNodeTrait.doRemoveData(MTree.java:852)
	at mtree.MTree$Node.doRemoveData(MTree.java:525)
	at mtree.MTree$Node.removeData(MTree.java:569)
	at mtree.MTree$RootNode.removeData(MTree.java:954)
	at mtree.MTree.remove(MTree.java:375)
	at outlierdetection.MTreeClass.remove(ExactStorm.java:206)
	at outlierdetection.MicroCluster.processShrinkCluster(MicroCluster.java:135)
	at outlierdetection.MicroCluster.purgeExpiredData(MicroCluster.java:104)
	at outlierdetection.MicroCluster.detectOutlier(MicroCluster.java:41)
	at mtree.tests.MTTest.main(MTTest.java:52)
Exception in thread "main" java.lang.RuntimeException: error on mtree.remove(cluster), cluster=outlierdetection.MCObject@1, cluster.arriveTime=446855
	at outlierdetection.MicroCluster.processShrinkCluster(MicroCluster.java:136)
	at outlierdetection.MicroCluster.purgeExpiredData(MicroCluster.java:104)
	at outlierdetection.MicroCluster.detectOutlier(MicroCluster.java:41)
	at mtree.tests.MTTest.main(MTTest.java:52)
