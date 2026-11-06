/** Contratos da API REST (ver DTOs em backend/core-api e backend/asset-management). */

export interface User {
    id: string;
    name: string;
    email: string;
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
        source: 'OBSERVED' | 'REFERENCE';
        annualReturn: number;
        annualVolatility: number;
        confidenceLevel: number;
        historyDays: number;
    };
    history: { date: string; value: number }[];
    projection: { date: string; expected: number; lower: number; upper: number }[];
    alerts: { severity: 'INFO' | 'WARNING'; title: string; message: string; suggestion: string | null }[];
}
