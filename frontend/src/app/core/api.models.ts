/** Contratos da API REST (ver DTOs em backend/core-api e backend/asset-management). */

export type Role = 'USER' | 'ADMIN';

export interface User {
    id: string;
    name: string;
    email: string;
    role: Role;
}

export interface LoginRequest {
    email: string;
    password: string;
}

export type InvestorProfile = 'CONSERVATIVE' | 'MODERATE' | 'AGGRESSIVE';

/** Dados opcionais do perfil. */
export interface ProfileDetails {
    phone: string | null;
    birthDate: string | null;
    investorProfile: InvestorProfile | null;
}

export interface RegisterRequest extends LoginRequest, ProfileDetails {
    name: string;
    confirmPassword: string;
}

export interface Profile extends User, ProfileDetails {
    createdAt: string;
}

export interface ChangePasswordRequest {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
}

export type AssetType = 'STOCK' | 'CRYPTO';
export type TransactionType = 'BUY' | 'SELL';

/** Ativo do catálogo, compartilhado entre os usuários. */
export interface Asset {
    symbol: string;
    name: string;
    assetType: AssetType;
}

/** Ativo encontrado no provedor de cotações; sem cotação, `price` é nulo. */
export interface AssetCandidate extends Asset {
    price: number | null;
}

/** Com `available` falso, o provedor não respondeu: isso não significa que o ativo não existe. */
export interface AssetLookup {
    available: boolean;
    candidates: AssetCandidate[];
}

export interface Position {
    symbol: string;
    name: string;
    assetType: AssetType;
    quantity: number;
    averagePrice: number;
}

/** Sem cotação, `currentPrice` é nulo e o valor de mercado usa o preço médio, com lucro zero. */
export interface DashboardPosition extends Position {
    currentPrice: number | null;
    investedValue: number;
    marketValue: number;
    profitLoss: number;
    profitLossPercent: number;
    quoteUpdatedAt: string | null;
}

export interface DashboardOverview {
    investedValue: number;
    marketValue: number;
    profitLoss: number;
    profitLossPercent: number;
    realizedProfit: number;
    positionsWithoutQuote: number;
    quotesUpdatedAt: string | null;
}

export interface PortfolioHistoryPoint {
    time: string;
    investedBalance: number;
    totalBalance: number;
}

/** Comportamento de mercado de um ativo em carteira. Campos ficam nulos sem preços suficientes para calculá-los. */
export interface MarketAsset {
    symbol: string;
    change24hPercent: number | null;
    periodChangePercent: number | null;
    periodLow: number | null;
    periodHigh: number | null;
    /** Do mais antigo ao mais recente. */
    prices: PricePoint[];
}

export interface PricePoint {
    time: string;
    price: number;
}

export interface Transaction {
    id: string;
    type: TransactionType;
    symbol: string;
    quantity: number;
    unitPrice: number;
    fee: number;
    totalAmount: number;
    /** Só nas vendas. */
    realizedProfit: number | null;
    timestamp: string;
    reversedAt: string | null;
}

export type TransactionStatus = 'ACTIVE' | 'REVERSED';

/** Formato do simplePaginate do Laravel: sem total, a API só diz se existe uma próxima página. */
export interface SimplePage<T> {
    current_page: number;
    data: T[];
    first_page_url: string;
    from: number | null;
    next_page_url: string | null;
    path: string;
    per_page: number;
    prev_page_url: string | null;
    to: number | null;
}

/** Quando a operação aconteceu, em ISO 8601. Sem valor, é agora. */
export interface OperationDate {
    executedAt?: string;
}

export interface BuyRequest extends OperationDate {
    symbol: string;
    name?: string;
    assetType: AssetType;
    quantity: number;
    unitPrice: number;
    fee: number;
    /** Cadastra o ativo mesmo que o provedor de cotações não o encontre. */
    acceptUnverified?: boolean;
}

export interface SellRequest extends OperationDate {
    symbol: string;
    quantity: number;
    unitPrice: number;
    fee: number;
}

export type Scenario = 'OPTIMISTIC' | 'REALISTIC' | 'PESSIMISTIC';

export interface SimulationRequest {
    months: number;
    monthlyContribution: number;
    scenario: Scenario;
}

export interface SimulationResponse {
    scenario: Scenario;
    months: number;
    monthlyContribution: number;
    currentValue: number;
    totalContributions: number;
    /** Retorno anual usado no cenário: o base com o ajuste de cada cenário. */
    scenarioReturn: number;
    assumptions: {
        source: 'MARKET' | 'BLENDED' | 'REFERENCE';
        annualReturn: number;
        annualVolatility: number;
        confidenceLevel: number;
        historyDays: number;
        /** Fração da carteira, de 0 a 1, medida no histórico de cotações. */
        coverage: number;
    };
    history: { date: string; value: number }[];
    projection: { date: string; expected: number; lower: number; upper: number }[];
    alerts: { severity: 'INFO' | 'WARNING'; title: string; message: string; suggestion: string | null }[];
}

export interface PublicSettings {
    registrationEnabled: boolean;
}

export interface Page<T> {
    items: T[];
    total: number;
}

export interface AdminUser extends Profile {
    role: Role;
    active: boolean;
    lastLoginAt: string | null;
}

export interface AdminUserUpdate extends ProfileDetails {
    name: string;
    email: string;
    role: Role;
}

export interface AdminUserCreate extends AdminUserUpdate {
    password: string;
}

export type VerificationStatus = 'VERIFIED' | 'UNVERIFIED';

export interface CatalogAsset extends Asset {
    id: string;
    verificationStatus: VerificationStatus;
    openPositions: number;
    transactions: number;
}

export interface AssetVerification {
    asset: CatalogAsset;
    check: 'FOUND' | 'NOT_FOUND' | 'UNAVAILABLE';
}

export interface AdminOverview {
    totalUsers: number;
    activeUsers: number;
    admins: number;
    wallets: number;
    transactions: number;
    collectionEnabled: boolean;
    /** Última coleta geral que atualizou preços. */
    lastCollectedAt: string | null;
    activeSymbols: number;
    symbolsWithoutQuote: string[];
    symbolsWithStaleQuote: string[];
}

export interface AdminSetting {
    key: string;
    group: string;
    label: string;
    description: string;
    type: 'BOOLEAN' | 'INTEGER' | 'PERCENT';
    min: number | null;
    max: number | null;
    /** Em texto, como guardado. Percentuais em fração: 0.05 é 5%. */
    value: string;
    defaultValue: string;
    overridden: boolean;
}

export interface SystemInfo {
    cryptoProvider: string;
    stockProvider: string;
    brapiTokenConfigured: boolean;
    collectionCron: string;
    collectOnStartup: boolean;
    predictiveEnabled: boolean;
}

export interface AdminSettings {
    settings: AdminSetting[];
    system: SystemInfo;
}

/** O que o usuário espera do ativo. Preço nulo significa que ele não o definiu. */
export interface WatchlistPlan {
    buyBelow: number | null;
    sellAbove: number | null;
    stopLoss: number | null;
    note: string | null;
}

export interface AddWatchlistItem extends WatchlistPlan {
    symbol: string;
    name?: string;
    assetType: AssetType;
    acceptUnverified: boolean;
}

/** Sem posição aberta, `quantity` é zero e `averagePrice` é nulo. Sem cotação, os dados de mercado são nulos. */
export interface WatchlistItem extends WatchlistPlan {
    id: string;
    symbol: string;
    name: string;
    assetType: AssetType;
    currentPrice: number | null;
    quoteUpdatedAt: string | null;
    change24hPercent: number | null;
    periodChangePercent: number | null;
    periodLow: number | null;
    periodHigh: number | null;
    prices: PricePoint[];
    quantity: number;
    averagePrice: number | null;
    createdAt: string;
}

export interface SyncStatus {
    /** Última coleta geral que atualizou preços; nula se nunca houve. */
    lastSyncAt: string | null;
    /** Nula com a coleta pausada ou sem agendamento. */
    nextSyncAt: string | null;
    lastRunFailed: boolean;
    collectionEnabled: boolean;
}

export type SyncOrigin = 'SCHEDULED' | 'STARTUP' | 'ADMIN' | 'NEW_SYMBOL';
export type SyncRunStatus = 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'INTERRUPTED';
export type SyncItemStatus = 'UPDATED' | 'NO_DATA' | 'FAILED';

export interface SyncRun {
    id: string;
    origin: SyncOrigin;
    status: SyncRunStatus;
    startedAt: string;
    finishedAt: string | null;
    requestedSymbols: number;
    updatedSymbols: number;
    message: string | null;
}

export interface SyncItem {
    symbol: string;
    assetClass: 'STOCK' | 'CRYPTO';
    kind: 'QUOTE' | 'HISTORY';
    status: SyncItemStatus;
    /** Fechamentos gravados, só na carga de histórico. */
    points: number | null;
    message: string | null;
}

export interface SyncRunFilter {
    status: SyncRunStatus | null;
    origin: SyncOrigin | null;
    symbol: string;
}

export interface AuditEntry {
    id: string;
    actorEmail: string;
    action: string;
    target: string | null;
    details: string | null;
    createdAt: string;
}
