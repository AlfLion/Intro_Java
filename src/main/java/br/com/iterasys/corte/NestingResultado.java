package br.com.iterasys.corte;

/**
 * Resultado do encaixe (nesting) de uma peça em fileiras ao longo da largura de um material,
 * empilhadas ao longo do comprimento aberto do rolo.
 */
public record NestingResultado(int pecasPorFileira, int fileiras, double comprimentoNecessarioMm) {

    public int capacidadeTotal() {
        return pecasPorFileira * fileiras;
    }
}
