package br.com.iterasys.corte;

/**
 * Encaixe (nesting) simples em grade: peças alinhadas lado a lado ao longo da largura
 * disponível, em fileiras empilhadas ao longo do comprimento. Sem rotação de peça.
 */
public final class CalculadoraNesting {

    private CalculadoraNesting() {
    }

    /**
     * Quanto comprimento é preciso abrir do material para encaixar toda a
     * quantidade demandada da peça, dada uma largura disponível.
     */
    public static NestingResultado calcularAberturaNecessaria(double larguraDisponivelMm, Peca peca) {
        int pecasPorFileira = pecasPorFileira(larguraDisponivelMm, peca);
        int fileiras = (int) Math.ceil((double) peca.getQuantidade() / pecasPorFileira);
        double comprimentoNecessarioMm = fileiras * peca.getComprimentoMm();
        return new NestingResultado(pecasPorFileira, fileiras, comprimentoNecessarioMm);
    }

    /**
     * Quantas unidades da peça cabem em uma área limitada tanto em largura quanto em
     * comprimento (ex.: uma placa de tamanho fixo).
     */
    public static int calcularCapacidadeEmArea(double larguraDisponivelMm, double comprimentoDisponivelMm, Peca peca) {
        int pecasPorFileira = pecasPorFileira(larguraDisponivelMm, peca);
        int fileiras = (int) Math.floor(comprimentoDisponivelMm / peca.getComprimentoMm());
        return pecasPorFileira * fileiras;
    }

    private static int pecasPorFileira(double larguraDisponivelMm, Peca peca) {
        int pecasPorFileira = pecasPorFileiraOuZero(larguraDisponivelMm, peca);
        if (pecasPorFileira <= 0) {
            throw new IllegalArgumentException(
                    "a peça '" + peca.getNome() + "' (largura " + peca.getLarguraMm()
                            + "mm) não cabe na largura disponível de " + larguraDisponivelMm + "mm");
        }
        return pecasPorFileira;
    }

    /**
     * Igual a {@link #pecasPorFileira}, mas devolve 0 em vez de lançar exceção quando a
     * peça não cabe — usado quando o chamador precisa avaliar se um modo de corte é
     * viável antes de decidir se o usa.
     */
    public static int pecasPorFileiraOuZero(double larguraDisponivelMm, Peca peca) {
        return (int) Math.floor(larguraDisponivelMm / peca.getLarguraMm());
    }
}
