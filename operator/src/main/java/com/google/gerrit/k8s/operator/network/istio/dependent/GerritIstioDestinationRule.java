// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.k8s.operator.network.istio.dependent;

import com.google.gerrit.k8s.operator.cluster.model.GerritCluster;
import com.google.gerrit.k8s.operator.gerrit.dependent.GerritService;
import com.google.gerrit.k8s.operator.gerrit.model.GerritTemplate;
import com.google.gerrit.k8s.operator.network.model.GerritNetwork;
import io.fabric8.istio.api.networking.v1beta1.DestinationRule;
import io.fabric8.istio.api.networking.v1beta1.DestinationRuleBuilder;
import io.fabric8.istio.api.networking.v1beta1.LoadBalancerSettingsSimpleLB;
import io.fabric8.istio.api.networking.v1beta1.TrafficPolicyBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GerritIstioDestinationRule
    extends KubernetesDependentResource<DestinationRule, GerritNetwork>
    implements Creator<DestinationRule, GerritNetwork>,
        Updater<DestinationRule, GerritNetwork>,
        Deleter<GerritNetwork>,
        BulkDependentResource<DestinationRule, GerritNetwork>,
        GarbageCollected<GerritNetwork> {

  public GerritIstioDestinationRule() {
    super(DestinationRule.class);
  }

  protected DestinationRule desired(GerritNetwork gerritNetwork, String gerritName) {

    return new DestinationRuleBuilder()
        .withNewMetadata()
        .withName(getName(gerritName))
        .withNamespace(gerritNetwork.getMetadata().getNamespace())
        .withLabels(
            GerritCluster.getLabels(
                gerritNetwork.getMetadata().getName(),
                getName(gerritName),
                this.getClass().getSimpleName()))
        .endMetadata()
        .withNewSpec()
        .withHost(GerritService.getHostname(gerritName, gerritNetwork.getMetadata().getNamespace()))
        .withTrafficPolicy(
            new TrafficPolicyBuilder()
                .withNewLoadBalancer()
                .withNewLoadBalancerSettingsSimpleLbPolicy()
                .withSimple(LoadBalancerSettingsSimpleLB.LEAST_CONN)
                .endLoadBalancerSettingsSimpleLbPolicy()
                .endLoadBalancer()
                .build())
        .endSpec()
        .build();
  }

  public static String getName(GerritTemplate gerrit) {
    return gerrit.getMetadata().getName();
  }

  public static String getName(String gerritName) {
    return gerritName;
  }

  @Override
  public Map<String, DestinationRule> desiredResources(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Map<String, DestinationRule> drs = new HashMap<>();
    if (gerritNetwork.hasPrimaryGerrit()) {
      String primaryGerritName = gerritNetwork.getSpec().getPrimaryGerrit().getName();
      drs.put(primaryGerritName, desired(gerritNetwork, primaryGerritName));
    }
    if (gerritNetwork.hasGerritReplica()) {
      String gerritReplicaName = gerritNetwork.getSpec().getGerritReplica().getName();
      drs.put(gerritReplicaName, desired(gerritNetwork, gerritReplicaName));
    }
    return drs;
  }

  @Override
  public Map<String, DestinationRule> getSecondaryResources(
      GerritNetwork gerritNetwork, Context<GerritNetwork> context) {
    Set<DestinationRule> drs = context.getSecondaryResources(DestinationRule.class);
    Map<String, DestinationRule> result = new HashMap<>(drs.size());
    for (DestinationRule dr : drs) {
      result.put(dr.getMetadata().getName(), dr);
    }
    return result;
  }
}