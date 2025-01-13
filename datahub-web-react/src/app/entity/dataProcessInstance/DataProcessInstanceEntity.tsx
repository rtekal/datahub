import React from 'react';
import { ApiOutlined } from '@ant-design/icons';
import {
    DataProcessInstance,
    Entity as GeneratedEntity,
    EntityType,
    OwnershipType,
    SearchResult,
} from '../../../types.generated';
import { Preview } from './preview/Preview';
import { Entity, EntityCapabilityType, IconStyleType, PreviewType } from '../Entity';
import { EntityProfile } from '../shared/containers/profile/EntityProfile';
import { useGetDataProcessInstanceQuery } from '../../../graphql/dataProcessInstance.generated';
import { PropertiesTab } from '../shared/tabs/Properties/PropertiesTab';
import { LineageTab } from '../shared/tabs/Lineage/LineageTab';
import { SidebarAboutSection } from '../shared/containers/profile/sidebar/AboutSection/SidebarAboutSection';
import { SidebarTagsSection } from '../shared/containers/profile/sidebar/SidebarTagsSection';
import { SidebarOwnerSection } from '../shared/containers/profile/sidebar/Ownership/sidebar/SidebarOwnerSection';
import { GenericEntityProperties } from '../shared/types';
import { getDataForEntityType } from '../shared/containers/profile/utils';
import { SidebarDomainSection } from '../shared/containers/profile/sidebar/Domain/SidebarDomainSection';
import { EntityMenuItems } from '../shared/EntityDropdown/EntityDropdown';
import { capitalizeFirstLetterOnly } from '../../shared/textUtil';
import DataProductSection from '../shared/containers/profile/sidebar/DataProduct/DataProductSection';
import { getDataProduct } from '../shared/utils';
// import SummaryTab from './profile/DataProcessInstaceSummary';

// const getProcessPlatformName = (data?: DataProcessInstance): string => {
//     return (
//         data?.dataPlatformInstance?.platform?.properties?.displayName ||
//         capitalizeFirstLetterOnly(data?.dataPlatformInstance?.platform?.name) ||
//         ''
//     );
// };

const getParentEntities = (data: DataProcessInstance): GeneratedEntity[] => {
    const parentEntity = data?.relationships?.relationships?.find(
        (rel) => rel.type === 'InstanceOf' && rel.entity?.type === EntityType.DataJob,
    );

    if (!parentEntity?.entity) return [];

    // Convert to GeneratedEntity
    return [
        {
            type: parentEntity.entity.type,
            urn: (parentEntity.entity as any).urn, // Make sure urn exists
            relationships: (parentEntity.entity as any).relationships,
        },
    ];
};
/**
 * Definition of the DataHub DataProcessInstance entity.
 */
export class DataProcessInstanceEntity implements Entity<DataProcessInstance> {
    type: EntityType = EntityType.DataProcessInstance;

    icon = (fontSize: number, styleType: IconStyleType, color?: string) => {
        if (styleType === IconStyleType.TAB_VIEW) {
            return <ApiOutlined style={{ fontSize, color }} />;
        }

        if (styleType === IconStyleType.HIGHLIGHT) {
            return <ApiOutlined style={{ fontSize, color: color || '#B37FEB' }} />;
        }

        return (
            <ApiOutlined
                style={{
                    fontSize,
                    color: color || '#BFBFBF',
                }}
            />
        );
    };

    isSearchEnabled = () => true;

    isBrowseEnabled = () => true;

    isLineageEnabled = () => true;

    getAutoCompleteFieldName = () => 'name';

    getPathName = () => 'dataProcessInstance';

    getEntityName = () => 'Process Instance';

    getGraphName = () => 'dataProcessInstance';

    getCollectionName = () => 'Process Instances';

    useEntityQuery = useGetDataProcessInstanceQuery;

    renderProfile = (urn: string) => (
        <EntityProfile
            urn={urn}
            entityType={EntityType.DataProcessInstance}
            useEntityQuery={this.useEntityQuery}
            // useUpdateQuery={useUpdateDataProcessInstanceMutation}
            getOverrideProperties={this.getOverridePropertiesFromEntity}
            headerDropdownItems={new Set([EntityMenuItems.UPDATE_DEPRECATION, EntityMenuItems.RAISE_INCIDENT])}
            tabs={[
                // {
                //     name: 'Documentation',
                //     component: DocumentationTab,
                // },
                // {
                //     name: 'Summary',
                //     component: SummaryTab,
                // },
                {
                    name: 'Lineage',
                    component: LineageTab,
                },
                {
                    name: 'Properties',
                    component: PropertiesTab,
                },
                // {
                //     name: 'Incidents',
                //     component: IncidentTab,
                //     getDynamicName: (_, processInstance) => {
                //         const activeIncidentCount = processInstance?.dataProcessInstance?.activeIncidents.total;
                //         return `Incidents${(activeIncidentCount && ` (${activeIncidentCount})`) || ''}`;
                //     },
                // },
            ]}
            sidebarSections={this.getSidebarSections()}
        />
    );

    getSidebarSections = () => [
        {
            component: SidebarAboutSection,
        },
        {
            component: SidebarOwnerSection,
            properties: {
                defaultOwnerType: OwnershipType.TechnicalOwner,
            },
        },
        {
            component: SidebarTagsSection,
            properties: {
                hasTags: true,
                hasTerms: true,
            },
        },
        {
            component: SidebarDomainSection,
        },
        {
            component: DataProductSection,
        },
    ];

    getOverridePropertiesFromEntity = (processInstance?: DataProcessInstance | null): GenericEntityProperties => {
        const name = processInstance?.name;
        const externalUrl = processInstance?.externalUrl;
        return {
            name,
            externalUrl,
        };
    };

    renderPreview = (_: PreviewType, data: DataProcessInstance) => {
        const genericProperties = this.getGenericEntityProperties(data);
        const parentEntities = getParentEntities(data);
        return (
            <Preview
                urn={data.urn}
                name={data.properties?.name || data.name || ''}
                subType={data.subTypes?.typeNames?.[0]}
                description=""
                platformName={
                    data?.platform?.properties?.displayName || capitalizeFirstLetterOnly(data?.platform?.name)
                }
                platformLogo={data.platform.properties?.logoUrl}
                owners={null}
                globalTags={null}
                // domain={data.domain?.domain}
                dataProduct={getDataProduct(genericProperties?.dataProduct)}
                externalUrl={data.properties?.externalUrl}
                parentContainers={data.parentContainers}
                parentEntities={parentEntities}
                container={data.container || undefined}
                // health={data.health}
            />
        );
    };

    renderSearch = (result: SearchResult) => {
        const data = result.entity as DataProcessInstance;
        const genericProperties = this.getGenericEntityProperties(data);
        const parentEntities = getParentEntities(data);
        return (
            <Preview
                urn={data.urn}
                name={data.properties?.name || data.name || ''}
                subType={data.subTypes?.typeNames?.[0]}
                description=""
                platformName={
                    data?.platform?.properties?.displayName || capitalizeFirstLetterOnly(data?.platform?.name)
                }
                platformLogo={data.platform.properties?.logoUrl}
                platformInstanceId={data.dataPlatformInstance?.instanceId}
                owners={null}
                globalTags={null}
                //                domain={data.domain?.domain}
                dataProduct={getDataProduct(genericProperties?.dataProduct)}
                //                deprecation={data.deprecation}
                insights={result.insights}
                externalUrl={data.properties?.externalUrl}
                degree={(result as any).degree}
                paths={(result as any).paths}
                parentContainers={data.parentContainers}
                parentEntities={parentEntities}
                container={data.container || undefined}
                // duration={data?.state?.[0]?.durationMillis}
                // status={data?.state?.[0]?.result?.resultType}
                // startTime={data?.state?.[0]?.timestampMillis}
                //                health={data.health}
            />
        );
    };

    getLineageVizConfig = (entity: DataProcessInstance) => {
        return {
            urn: entity?.urn,
            name: this.displayName(entity),
            type: EntityType.DataProcessInstance,
            subtype: entity?.subTypes?.typeNames?.[0],
            icon: entity?.platform?.properties?.logoUrl || undefined,
            platform: entity?.platform,
            container: entity?.container,
            //            health: entity?.health || undefined,
        };
    };

    displayName = (data: DataProcessInstance) => {
        return data.properties?.name || data.urn;
    };

    getGenericEntityProperties = (data: DataProcessInstance) => {
        return getDataForEntityType({
            data,
            entityType: this.type,
            getOverrideProperties: this.getOverridePropertiesFromEntity,
        });
    };

    supportedCapabilities = () => {
        return new Set([
            EntityCapabilityType.OWNERS,
            EntityCapabilityType.GLOSSARY_TERMS,
            EntityCapabilityType.TAGS,
            EntityCapabilityType.DOMAINS,
            EntityCapabilityType.DEPRECATION,
            EntityCapabilityType.SOFT_DELETE,
            EntityCapabilityType.DATA_PRODUCTS,
        ]);
    };
}
