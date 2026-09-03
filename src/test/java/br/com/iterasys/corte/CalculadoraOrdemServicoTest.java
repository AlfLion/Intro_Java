package br.com.iterasys.corte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraOrdemServicoTest {

    @Test
    void agregaConsumoPorMaterialESobrasDeVariosItens() {
        Material eva = new Material("EVA", 1000);
        Material poron = new Material("Poron", 1400);
        Material colaTransfer = new Material("Cola transfer", 1220);
        MaterialConjugado poronComCola = new MaterialConjugado(poron, colaTransfer);

        OrdemServico ordem = new OrdemServico("OS-001");
        ordem.adicionarItem(ItemOrdemServico.deMaterialUnico(new Peca("Base", 100, 50, 25), eva));
        ordem.adicionarItem(ItemOrdemServico.deMaterialConjugado(new Peca("Gaxeta", 200, 100, 262), poronComCola));
        ordem.adicionarItem(ItemOrdemServico.deMaterialConjugado(new Peca("Suporte", 600, 700, 5), poronComCola));

        ResumoOrdemServico resumo = CalculadoraOrdemServico.calcular(ordem);

        assertEquals(3, resumo.resultadosPorItem().size());
        assertEquals(2, resumo.sobras().size());

        // Suporte 600x700 (qtd 5) prefere placa girada: 1 placa cheia (1220/1400mm) + tira de 1 peça (600mm)
        assertEquals(150, resumo.consumoTotalPorMaterialMm().get("EVA"));
        assertEquals(3 * 1220 + 200 + 1 * 1220 + 600, resumo.consumoTotalPorMaterialMm().get("Poron"));
        assertEquals(3 * 1400 + 200 + 1 * 1400 + 600, resumo.consumoTotalPorMaterialMm().get("Cola transfer"));

        ResultadoItem resultadoGaxeta = resumo.resultadosPorItem().get(1);
        assertEquals(3, resultadoGaxeta.planoConjugado().placasCheias());
        assertEquals("Gaxeta", resultadoGaxeta.item().getPeca().getNome());

        ResultadoItem resultadoBase = resumo.resultadosPorItem().get(0);
        assertEquals(3, resultadoBase.nestingMaterialUnico().fileiras());
    }
}
