export type SentimentTone = 'extremeFear' | 'fear' | 'neutral' | 'greed' | 'extremeGreed';

export interface ChartTheme {
    text: string;
    grid: string;
    baseline: string;
    surface: string;
    /** Cores por série, nesta ordem: validada contra daltonismo para séries vizinhas, na superfície de cada tema. */
    series: readonly string[];
    /** Uma cor por faixa, mais forte longe do neutro. Validada contra daltonismo nesta ordem, para faixas vizinhas. */
    sentiment: Record<SentimentTone, string>;
}

const THEMES: Record<'light' | 'dark', ChartTheme> = {
    light: {
        text: '#64748b',
        grid: 'rgba(100, 116, 139, 0.15)',
        baseline: 'rgba(100, 116, 139, 0.6)',
        surface: '#ffffff',
        series: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300'],
        sentiment: { extremeFear: '#a61b12', fear: '#ea6a1f', neutral: '#2a78d6', greed: '#5a9e1f', extremeGreed: '#0a6b3a' }
    },
    dark: {
        text: '#a1a1aa',
        grid: 'rgba(161, 161, 170, 0.15)',
        baseline: 'rgba(161, 161, 170, 0.6)',
        surface: '#18181b',
        series: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300'],
        sentiment: { extremeFear: '#dc2f26', fear: '#cf8024', neutral: '#3987e5', greed: '#6ca62f', extremeGreed: '#157a4b' }
    }
};

export const chartTheme = (dark: boolean): ChartTheme => THEMES[dark ? 'dark' : 'light'];

/** Quantas séries um gráfico de linhas comporta antes de ficar ilegível; é também o tamanho da paleta. */
export const MAX_LINE_SERIES = THEMES.light.series.length;
