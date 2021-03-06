/*
 * Copyright 2014 NAVER Corp.
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

package com.navercorp.pinpoint.web.applicationmap;

import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.web.applicationmap.histogram.*;
import com.navercorp.pinpoint.web.applicationmap.rawdata.*;
import com.navercorp.pinpoint.web.view.AgentResponseTimeViewModelList;
import com.navercorp.pinpoint.web.view.LinkSerializer;
import com.navercorp.pinpoint.web.view.ResponseTimeViewModel;
import com.navercorp.pinpoint.web.vo.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class that describes relationship between apps in application map
 *
 * @author netspider
 * @author emeroad
 */
@JsonSerialize(using = LinkSerializer.class)
public class Link {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String LINK_DELIMITER = "~";

    // specifies who created the link.
    // indicates whether it was automatically created by the source, or if it was manually created by the target.
    private final CreateType createType;
    private final Node fromNode;
    private final Node toNode;

    private final Range range;

    private final LinkStateResolver linkStateResolver = LinkStateResolver.DEFAULT_LINK_STATE_RESOLVER;

    private final LinkCallDataMap sourceLinkCallDataMap = new LinkCallDataMap();

    private final LinkCallDataMap targetLinkCallDataMap = new LinkCallDataMap();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Histogram linkHistogram;



    public Link(CreateType createType, Node fromNode, Node toNode, Range range) {
        if (createType == null) {
            throw new NullPointerException("createType must not be null");
        }
        if (fromNode == null) {
            throw new NullPointerException("fromNode must not be null");
        }
        if (toNode == null) {
            throw new NullPointerException("toNode must not be null");
        }
        if (range == null) {
            throw new NullPointerException("range must not be null");
        }

        this.createType = createType;

        this.fromNode = fromNode;
        this.toNode = toNode;

        this.range = range;

    }

    public Application getFilterApplication() {
        // User link: need to look at WAS, not from
        // Since User is a virtual link, we cannot filter by User
        if (fromNode.getServiceType() == ServiceType.USER) {
            return toNode.getApplication();
        }
        return fromNode.getApplication();
    }


    public LinkKey getLinkKey() {
        return new LinkKey(fromNode.getApplication(), toNode.getApplication());
    }

    public Node getFrom() {
        return fromNode;
    }

    public Node getTo() {
        return toNode;
    }

    public String getLinkName() {
        return fromNode.getNodeName() + LINK_DELIMITER + toNode.getNodeName();
    }

    public LinkCallDataMap getSourceLinkCallDataMap() {
        return sourceLinkCallDataMap;
    }

    public LinkCallDataMap getTargetLinkCallDataMap() {
        return targetLinkCallDataMap;
    }

    @JsonIgnore
    public String getJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public AgentHistogramList getTargetList() {
        return sourceLinkCallDataMap.getTargetList();
    }


    public Histogram getHistogram() {
        if (linkHistogram == null) {
            linkHistogram = createHistogram0();
        }
        return linkHistogram;
    }

    private Histogram createHistogram0() {
        // need serviceType of target (callee)
        // ie. Tomcat -> Arcus: we need arcus type
        final LinkCallDataMap findMap = getLinkCallDataMap();
        AgentHistogramList targetList = findMap.getTargetList();
        return targetList.mergeHistogram(toNode.getServiceType());
    }

    private LinkCallDataMap getLinkCallDataMap() {
        switch (createType) {
            case Source:
                return sourceLinkCallDataMap;
            case Target:
                return targetLinkCallDataMap;
            default:
                throw new IllegalArgumentException("invalid CreateType:" + createType);
        }
    }

    public List<ResponseTimeViewModel> getLinkApplicationTimeSeriesHistogram() {
        if (createType == CreateType.Source)  {
            return getSourceApplicationTimeSeriesHistogram();
        } else {
            return getTargetApplicationTimeSeriesHistogram();
        }
    }

    public Histogram getTargetHistogram() {
        // need serviceType of target (callee)
        // ie. Tomcat -> Arcus: we need Arcus type
        AgentHistogramList targetList = targetLinkCallDataMap.getTargetList();
        return targetList.mergeHistogram(toNode.getServiceType());

    }

    @JsonIgnore
    public AgentHistogramList getSourceList() {
        return sourceLinkCallDataMap.getSourceList();
    }

    public void addSource(LinkCallDataMap sourceLinkCallDataMap) {
        this.sourceLinkCallDataMap.addLinkDataMap(sourceLinkCallDataMap);
    }

    public void addTarget(LinkCallDataMap targetLinkCallDataMap) {
        this.targetLinkCallDataMap.addLinkDataMap(targetLinkCallDataMap);
    }

    public List<ResponseTimeViewModel> getSourceApplicationTimeSeriesHistogram() {
        ApplicationTimeHistogram histogramData = getSourceApplicationTimeSeriesHistogramData();
        return histogramData.createViewModel();

    }

    private ApplicationTimeHistogram getSourceApplicationTimeSeriesHistogramData() {
        // we need Target (to)'s time since time in link is RPC-based
        ApplicationTimeHistogramBuilder builder = new ApplicationTimeHistogramBuilder(toNode.getApplication(), range);
        return builder.build(sourceLinkCallDataMap.getLinkDataList());
    }

    public ApplicationTimeHistogram getTargetApplicationTimeSeriesHistogramData() {
        // we need Target (to)'s time since time in link is RPC-based
        ApplicationTimeHistogramBuilder builder = new ApplicationTimeHistogramBuilder(toNode.getApplication(), range);
        return builder.build(targetLinkCallDataMap.getLinkDataList());
    }

    public AgentResponseTimeViewModelList getSourceAgentTimeSeriesHistogram() {

        // we need Target (to)'s time since time in link is RPC-based
        AgentTimeHistogramBuilder builder = new AgentTimeHistogramBuilder(toNode.getApplication(), range);
        AgentTimeHistogram applicationTimeSeriesHistogram = builder.buildSource(sourceLinkCallDataMap);
        AgentResponseTimeViewModelList agentResponseTimeViewModelList = new AgentResponseTimeViewModelList(applicationTimeSeriesHistogram.createViewModel());
        return agentResponseTimeViewModelList;
    }

    public AgentTimeHistogram getTargetAgentTimeHistogram() {
        AgentTimeHistogramBuilder builder = new AgentTimeHistogramBuilder(toNode.getApplication(), range);
        AgentTimeHistogram agentTimeHistogram = builder.buildSource(targetLinkCallDataMap);
        return agentTimeHistogram;
    }

    public Collection<Application> getSourceLinkTargetAgentList() {
        Set<Application> agentList = new HashSet<>();
        Collection<LinkCallData> linkDataList = sourceLinkCallDataMap.getLinkDataList();
        for (LinkCallData linkCallData : linkDataList) {
            agentList.add(new Application(linkCallData.getTarget(), linkCallData.getTargetServiceType()));
        }
        return agentList;
    }

    public List<ResponseTimeViewModel> getTargetApplicationTimeSeriesHistogram() {
        ApplicationTimeHistogram targetApplicationTimeHistogramData = getTargetApplicationTimeSeriesHistogramData();
        return targetApplicationTimeHistogramData.createViewModel();
    }


    public String getLinkState() {
        return linkStateResolver.resolve(this);
    }

    public Boolean getLinkAlert() {
        return linkStateResolver.isAlert(this);
    }

    public boolean isWasToWasLink() {
        return this.fromNode.getApplication().getServiceType().isWas() && this.toNode.getApplication().getServiceType().isWas();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Link link = (Link) o;

        if (!fromNode.equals(link.fromNode)) return false;
        if (!toNode.equals(link.toNode)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = fromNode.hashCode();
        result = 31 * result + toNode.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Link{" +
                "from=" + fromNode +
                " -> to=" + toNode +
                '}';
    }


}
