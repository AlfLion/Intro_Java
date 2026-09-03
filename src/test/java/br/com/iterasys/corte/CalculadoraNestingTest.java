package br.com.iterasys.corte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculadoraNestingTest {

    @Test
    void calculaFileirasEComprimentoNecessario() {
        Peca peca = new Peca("Gaxeta", 200, 100, 10);

        NestingResultado resultado = CalculadoraNesting.calcularAberturaNecessaria(1220, peca);

        assertEquals(6, resultado.pecasPorFileira());
        assertEquals(2, resultado.fileiras());
        assertEquals(200, resultado.comprimentoNecessarioMm());
    }

    @Test
    void pecaMaiorQueLarguraDisponivelLancaExcecao() {
        Peca peca = new Peca("Gaxeta grande", 1500, 100, 1);

        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraNesting.calcularAberturaNecessaria(1220, peca));
    }

    @Test
    void calculaCapacidadeLimitadaPorAreaFixa() {
        Peca peca = new Peca("Gaxeta", 200, 100, 1000);

        int capacidade = CalculadoraNesting.calcularCapacidadeEmArea(1400, 1220, peca);

        assertEquals(84, capacidade);
    }
}
