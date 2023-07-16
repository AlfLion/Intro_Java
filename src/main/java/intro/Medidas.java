//1 - Pacote
package intro;

//2 - Referencia as bibliotecas

import java.util.Scanner;

// 3 - Classe
public class Medidas {
    // 3.1 - Atributos - Caracteristicas

   // 3.2 - Métodos e funções
        public static void main(String[] args) {
            //Condicional = Verificar uma condição - Fazer uma pergunta para uma pssoa, um hardware ou softarware

            //utilizar a classe Scanner do Java para ler a escolha do usuario no console
            Scanner scanner = new Scanner(System.in);
            System.out.println("O P Ç Õ E S ");
            System.out.println("c - Calcular área modo curto");
            System.out.println("e - Calcular área modo extenso");
            System.out.println("d - Calcular área modo contar até Dez");
            System.out.println("i - Calcular área modo If Simples");
            System.out.println("r - Calcular área modo contagem regressiva");
            System.out.println("Digite a opção desejada: ");
            String opcao = scanner.next();



            //switch = selecionar o comportamento do programa conforme a escolha da pessoa ou software

            switch (opcao){
                case "c":
                case "C":
                    System.out.println("Você escolheu executar o método curto");
                    calcularAreaModoCompacto();
                    break;
                case "d":
                case "D":
                    System.out.println("Você escolheu executar o método contarAteDez");
                    contarAteDez();
                    break;
                case "e":
                case "E":
                    System.out.println("Você escolheu executar o método extenso");
                    calcularAreaModoExtenso();
                    break;
                case "i":
                case "I":
                    System.out.println("Você escolheu executar o método ifSimples");
                    ifSimples();
                    break;
                case "r":
                case "R":
                    System.out.println("Você escolheu executar o método regressiva");
                    regressiva();
                    break;
                default:
                    System.out.println("Você escolheu outro valor que nao tem metodo associado");
                    break;



            }
        }


    public static void ifSimples(){

        //if = se
        // else = senão

        String modo = "curto";
        if (modo == "curto"){
            calcularAreaModoCompacto();
        }
        else{
            calcularAreaModoExtenso();
        }
    }

        public static void calcularAreaModoExtenso(){
            //Calculo de área - exemplo: o tamanho do tapete ou do piso
            System.out.println("Cálculo de Areas - modo Extenso");
            int largura;
            int comprimento;
            int resultado;

            largura = 5; //largura recebe
            comprimento = 6; //comprimento recebe 3

            resultado = largura * comprimento;
            System.out.println("Para a largura de "+largura+"m e o comprimento de "
                    +comprimento+"m a area é de "+resultado+"m²");

        }
        public static void calcularAreaModoCompacto(){
            //Calculo de area reduzido
            System.out.println("Cálculo de Areas - modo curto");
            int largura = 4;
            int comprimento = 3;
            System.out.println("Para a largura de "+largura+"m e o comprimento de "
                    +comprimento+"m a area é de "+largura*comprimento+"m²");
        }

        public static void contarAteDez(){
            //Loops ou Repetições
            //for = repetição incondicional

            System.out.println("Contagem crescente");

            for(int numero = 1; numero<=10; numero++){
                System.out.println(numero);
            }
        }

    public static void regressiva(){
        //Loops ou Repetições
        //for = repetição incondicional

        System.out.println("regressiva");

        for(int numero = 10; numero> -1; numero--){
            System.out.println(numero);
        }
    }
}
