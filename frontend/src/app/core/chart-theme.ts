export interface ChartTheme {
    text: string;
    grid: string;
    baseline: string;
    surface: string;
    /** Cores por série, nesta ordem: validada contra daltonismo para séries vizinhas, na superfície de cada tema. */
    series: readonly string[];
    /** Medo, neutro e ganância. Validada contra daltonismo nesta ordem, com o azul separando o vermelho do verde. */
    sentiment: { fear: string; neutral: string; greed: string };
}

const THEMES: Record<'light' | 'dark', ChartTheme> = {
    light: {
        text: '#64748b',
        grid: 'rgba(100, 116, 139, 0.15)',
        baseline: 'rgba(100, 116, 139, 0.6)',
        surface: '#ffffff',
        series: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300'],
        sentiment: { fear: '#d92d20', neutral: '#2a78d6', greed: '#0a8a3f' }
    },
    dark: {
        text: '#a1a1aa',
        grid: 'rgba(161, 161, 170, 0.15)',
        baseline: 'rgba(161, 161, 170, 0.6)',
        surface: '#18181b',
        series: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300'],
        sentiment: { fear: '#f0584f', neutral: '#3987e5', greed: '#2fae63' }
    }
};

export const chartTheme = (dark: boolean): ChartTheme => THEMES[dark ? 'dark' : 'light'];

/** Quantas séries um gráfico de linhas comporta antes de ficar ilegível; é também o tamanho da paleta. */
export const MAX_LINE_SERIES = THEMES.light.series.length;
