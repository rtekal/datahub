import React, { useMemo } from 'react';
import styled from 'styled-components';

import { MATCH_COLOR, MATCH_COLOR_LEVEL } from '@app/searchV2/autoCompleteV2/constants';
import { MatchText, Text } from '@src/alchemy-components';
import { MatchesGroupedByFieldName } from '@src/app/search/matches/constants';
import { getMatchedFieldLabel } from '@src/app/search/matches/utils';
import { capitalizeFirstLetterOnly } from '@src/app/shared/textUtil';
import { EntityType } from '@src/types.generated';

const TextWrapper = styled.span`
    overflow: hidden;
    word-wrap: wrap;
    text-overflow: ellipsis;
`;

interface Props {
    query: string;
    entityType: EntityType;
    match: MatchesGroupedByFieldName;
}

export default function Match({ query, entityType, match }: Props) {
    const label = useMemo(
        () => capitalizeFirstLetterOnly(getMatchedFieldLabel(entityType, match.fieldName)),
        [entityType, match.fieldName],
    );
    // show only the first value
    const value = useMemo(() => match.matchedFields?.[0]?.value, [match]);

    if (value === undefined) return null;

    return (
        <TextWrapper>
            <Text color={MATCH_COLOR} colorLevel={MATCH_COLOR_LEVEL} size="sm" type="span">
                {label}: <MatchText size="sm" type="span" text={value} highlight={query} />
            </Text>
        </TextWrapper>
    );
}
