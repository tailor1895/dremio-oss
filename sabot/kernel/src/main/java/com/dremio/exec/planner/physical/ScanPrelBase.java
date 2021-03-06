/*
 * Copyright (C) 2017 Dremio Corporation
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
 */
package com.dremio.exec.planner.physical;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import com.dremio.common.expression.SchemaPath;
import com.dremio.exec.planner.common.ScanRelBase;
import com.dremio.exec.planner.fragment.DistributionAffinity;
import com.dremio.exec.planner.logical.Rel;
import com.dremio.exec.planner.physical.visitor.PrelVisitor;
import com.dremio.exec.record.BatchSchema.SelectionVectorMode;
import com.dremio.exec.store.TableMetadata;
import com.dremio.service.namespace.StoragePluginId;
import com.dremio.service.namespace.capabilities.SourceCapabilities;
import com.dremio.service.namespace.dataset.proto.Affinity;
import com.dremio.service.namespace.dataset.proto.DatasetSplit;


public abstract class ScanPrelBase extends ScanRelBase implements Prel, HasDistributionAffinity {

  public ScanPrelBase(RelOptCluster cluster, RelTraitSet traitSet, RelOptTable table, StoragePluginId pluginId,
      TableMetadata dataset, List<SchemaPath> projectedColumns, double observedRowcountAdjustment) {
    super(cluster, traitSet, table, pluginId, dataset, projectedColumns, observedRowcountAdjustment);
    assert traitSet.getTrait(ConventionTraitDef.INSTANCE) != Rel.LOGICAL;
  }

  public int getMaxParallelizationWidth(){
    return tableMetadata.getSplitCount();
  }

  public int getMinParallelizationWidth() {
    if(getDistributionAffinity() != DistributionAffinity.HARD){
      return 1;
    }

    final Set<String> nodes = new HashSet<>();
    Iterator<DatasetSplit> iter = tableMetadata.getSplits();
    while(iter.hasNext()){
      DatasetSplit split = iter.next();
      for(Affinity a : split.getAffinitiesList()){
        nodes.add(a.getHost());
      }
    }

    return nodes.size();
  }

  @Override
  public Iterator<Prel> iterator() {
    return Collections.emptyIterator();
  }

  @Override
  public <T, X, E extends Throwable> T accept(PrelVisitor<T, X, E> logicalVisitor, X value) throws E {
    return logicalVisitor.visitScan(this, value);
  }

  @Override
  public SelectionVectorMode[] getSupportedEncodings() {
    return SelectionVectorMode.DEFAULT;
  }

  @Override
  public SelectionVectorMode getEncoding() {
    return SelectionVectorMode.NONE;
  }

  @Override
  public boolean needsFinalColumnReordering() {
    return true;
  }

  @Override
  public DistributionAffinity getDistributionAffinity() {
    return this.tableMetadata.getStoragePluginId().getCapabilities().getCapability(SourceCapabilities.REQUIRES_HARD_AFFINITY) ? DistributionAffinity.HARD : DistributionAffinity.SOFT;
  }


}
