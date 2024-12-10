import { Icon } from '@components';
import { colors, radius, spacing, typography } from '@src/alchemy-components/theme';
import { AlignmentOptions } from '@src/alchemy-components/theme/config';
import styled from 'styled-components';

export const TableContainer = styled.div<{ isScrollable?: boolean; maxHeight?: string }>(
    ({ isScrollable, maxHeight }) => ({
        borderRadius: radius.lg,
        border: `1px solid ${colors.gray[1400]}`,
        overflow: isScrollable ? 'auto' : 'hidden',
        width: '100%',
        maxHeight: maxHeight || '100%',
    }),
);

export const BaseTable = styled.table({
    borderCollapse: 'collapse',
    width: '100%',
});

export const TableHeader = styled.thead({
    backgroundColor: colors.gray[1500],
    borderRadius: radius.lg,
    position: 'sticky',
    top: 0,
    zIndex: 100,
});

export const TableHeaderCell = styled.th<{ width?: string }>(({ width }) => ({
    padding: `${spacing.sm} ${spacing.md}`,
    color: colors.gray[600],
    fontSize: typography.fontSizes.sm,
    fontWeight: typography.fontWeights.medium,
    textAlign: 'start',
    width: width || 'auto',
}));

export const HeaderContainer = styled.div({
    display: 'flex',
    alignItems: 'center',
    gap: spacing.sm,
});

export const TableRow = styled.tr({
    '&:last-child': {
        '& td': {
            borderBottom: 'none',
        },
    },

    '& td:first-child': {
        fontWeight: typography.fontWeights.medium,
        color: colors.gray[600],
    },
});

export const TableCell = styled.td<{ width?: string; alignment?: AlignmentOptions }>(({ width, alignment }) => ({
    padding: spacing.md,
    borderBottom: `1px solid ${colors.gray[1400]}`,
    color: colors.gray[1700],
    fontSize: typography.fontSizes.md,
    fontWeight: typography.fontWeights.normal,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    maxWidth: width || 'unset',
    textAlign: alignment || 'left',
}));

export const SortIconsContainer = styled.div({
    display: 'flex',
    flexDirection: 'column',
});

export const SortIcon = styled(Icon)<{ isActive?: boolean }>(({ isActive }) => ({
    margin: '-3px',
    stroke: isActive ? colors.violet[600] : undefined,

    ':hover': {
        cursor: 'pointer',
    },
}));

export const LoadingContainer = styled.div({
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
    width: '100%',
    gap: spacing.sm,
    color: colors.violet[700],
    fontSize: typography.fontSizes['3xl'],
});
